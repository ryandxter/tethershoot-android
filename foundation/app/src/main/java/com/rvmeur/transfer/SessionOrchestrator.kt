package com.rvmeur.transfer

import android.content.Context
import android.hardware.usb.UsbDevice
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * State machine orkestrasi sesi tether USB PTP untuk TransferIn Foundation (Stage 1).
 *
 * Menerapkan MODEL ANTREAN (FIFO QueueItem) sesuai spesifikasi STAGE1-QUEUE-FIX-SPEC:
 * - Identitas item kanonik: filename|captureDate|sizeBytes (bukan handle).
 * - Penemuan (polling) hanya MENAMBAH item QUEUED ke antrean FIFO.
 * - Set knownHandles HANYA ditambah saat ObjectInfo berhasil dibaca, tidak pernah di-clear di tengah sesi.
 * - Pekerja penguras serial menyelesaikan handle segar per item dari enumerasi terbaru sebelum unduh.
 * - Checkout DeleteObject hanya sesudah unduh byte-exact.
 * - Item gagal unduh = FAILED tanpa delete; item tidak ketemu handle-nya = ORPHANED dengan log keras.
 * - Baseline membangun set baselineIdentities dari GetObjectInfo tiap handle awal (BRS Q3).
 * - Teardown jujur melaporkan sisa antrean + D20E apa adanya.
 */
class SessionOrchestrator(
    private val context: Context,
    val ptpSession: PtpSession,
    @Volatile var db: TransferInDb? = null,
    @Volatile var uploadQueue: UploadQueue? = null,
    @Volatile var targetDriveFolderId: String? = null,
    @Volatile var cullingGate: CullingGate? = null
) {

    enum class State {
        IDLE,
        CONNECTING,
        BASELINE,
        MONITORING,
        CLOSING,
        CLOSED,
        ERROR
    }

    enum class ItemState {
        QUEUED,
        DOWNLOADING,
        DONE,
        FAILED,
        ORPHANED
    }

    data class QueueItem(
        val identity: String,
        val filename: String,
        val captureDateString: String,
        val sizeBytes: Long,
        val formatCode: Int,
        val groupSecond: String,
        var state: ItemState = ItemState.QUEUED,
        val firstSeenElapsedMs: Long = System.currentTimeMillis()
    )

    data class Statistics(
        val slotRuangTunggu: Int = 12,
        val grupSelesai: Int = 0,
        val berkasSelesai: Int = 0,
        val totalBytesDownloaded: Long = 0L,
        val lastDownloadMbPerSec: Double = 0.0,
        val gagalCount: Int = 0,
        val cameraModel: String = "",
        val cameraSerial: String = ""
    ) {
        val totalMbDownloaded: Double
            get() = totalBytesDownloaded.toDouble() / (1024.0 * 1024.0)
    }

    interface Listener {
        fun onStateChanged(state: State)
        fun onStatisticsUpdated(stats: Statistics)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var statistics: Statistics = Statistics()
        private set

    @Volatile
    var currentSessionId: String? = null
        private set

    @Volatile
    private var pollNow: Boolean = false

    @Volatile
    private var isClosing: Boolean = false

    private val running = AtomicBoolean(false)
    private var orchestratorThread: Thread? = null

    // Set handle yang sudah berhasil di-GetObjectInfo sesi ini (TIDAK PERNAH di-clear di tengah sesi)
    private val knownHandles = HashSet<Int>()

    // Kunci sinkronisasi antrean FIFO
    private val queueLock = Any()

    // Koleksi sesi & antrean FIFO
    private val queue = ArrayDeque<QueueItem>()
    private val doneItems = mutableListOf<QueueItem>()
    private val failedItems = mutableListOf<QueueItem>()
    private val orphanedItems = mutableListOf<QueueItem>()

    private val sessionIdentities = HashSet<String>()
    private val identityHandles = HashMap<String, Int>()
    private val downloadAttempts = mutableMapOf<String, Int>()
    private val deadIdentities = mutableSetOf<String>()

    /** Jendela tenggang aturan lolos timestamp garis pangkal (detik). */
    private val BASELINE_GRACE_SECONDS = 10L
    private val BASELINE_FUTURE_GRACE_SECONDS = 90L
    private val BUANG_SISA_SAFE_MARGIN_SECONDS = 120L

    /** Jumlah sisa kamera (penghuni eksklusi garis pangkal) sesi berjalan — dibaca UI. */
    @Volatile
    var sisaKameraCount: Int = 0
        private set

    @Volatile
    private var buangSisaRequested = false

    /**
     * Buang Sisa Kamera (keputusan user, 9 Nov 2026): bebaskan slot foto lama
     * (eksklusi garis pangkal) langsung dari ruang tunggu kamera — tanpa
     * mengunduh, tanpa mengunggah; salinan kartu tidak tersentuh. Permintaan
     * dicatat di sini dan DIEKSEKUSI thread orkestrator sendiri di sela
     * antrean, supaya seluruh perintah PTP tetap serial di satu thread.
     */
    fun requestBuangSisaKamera() {
        buangSisaRequested = true
        FoundationLog.log("[SISA] Permintaan Buang Sisa Kamera diterima — dijalankan orkestrator di sela antrean")
    }

    private val baselineIdentities = HashSet<String>()
    private val skippedBaselineIdentities = HashSet<String>()
    private val detectedGroupSeconds = HashSet<String>()
    private val checkedOutGroups = HashSet<String>()

    /** Peta instans grup per jepretan: captureDateString mentah -> daftar himpunan kelas ekstensi per instans. */
    private val groupInstanceSlots = mutableMapOf<String, MutableList<MutableSet<String>>>()
    private val groupKeyByIdentity = mutableMapOf<String, String>()

    /** Nama dasar kanonis per grup jepret untuk penamaan pasangan 1:1. */
    private val canonicalBaseByGroup = HashMap<String, String>()

    /** Akuntansi kelengkapan grup: anggota terdeteksi (saat ANTRE) vs tersisip ke DB. */
    private val detectedGroupSizes = HashMap<String, Int>()
    private val insertedGroupCounts = HashMap<String, Int>()
    private val groupDetectedChangedAt = mutableMapOf<String, Long>()
    private val groupReadyCandidates = mutableSetOf<String>()

    private fun assignGroupKey(captureSecondRaw: String, filename: String): String {
        val lower = filename.lowercase(Locale.US)
        val extClass = when {
            lower.endsWith(".raf") -> "RAF"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "JPG"
            else -> filename.substringAfterLast('.', "").uppercase(Locale.US)
        }
        val instances = groupInstanceSlots.getOrPut(captureSecondRaw) { mutableListOf() }
        val existingIdx = instances.indexOfFirst { extClass !in it }
        return if (existingIdx >= 0) {
            instances[existingIdx].add(extClass)
            if (existingIdx == 0) captureSecondRaw else "$captureSecondRaw#${existingIdx + 1}"
        } else {
            instances.add(mutableSetOf(extClass))
            val newIdx = instances.lastIndex
            if (newIdx == 0) captureSecondRaw else "$captureSecondRaw#${newIdx + 1}"
        }
    }

    /**
     * Penghitung nomor jepret per tanggal, persisten antar-sesi (keputusan
     * skema nama, 10 Nov 2026): sesi berikutnya di tanggal yang sama
     * melanjutkan nomor terakhir, sehingga nama IMG_<tanggal>_<NNN> unik
     * sepanjang hari dan tidak bentrok di folder tujuan bersama.
     */
    private fun nextImgSeq(datePart: String): Int {
        val prefs = context.getSharedPreferences("transferin-naming", Context.MODE_PRIVATE)
        val key = "imgSeq_$datePart"
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return next
    }

    @Volatile
    private var currentItemBeingDownloaded: QueueItem? = null

    init {
        // Callback petunjuk interrupt dari PtpSession (0x4002 ObjectAdded)
        ptpSession.onObjectAddedHint = { _ ->
            triggerPollNow()
        }
    }

    /**
     * Mempercepat siklus polling berikutnya (bukan jalur unduh terpisah).
     */
    /**
     * Stage 4: apakah grup (detik jepret) ini masih punya berkas yang sedang
     * diantrekan/diunduh di pipa kamera? Dipakai CullingGate sebagai pemeriksa
     * kesiapan cadangan — jalur utama kesiapan adalah markGroupReady.
     * Antrean dilayani satu sesi aktif pada satu waktu, jadi groupSecond cukup.
     */
    fun isGroupInFlight(sessionId: String, groupSecond: String): Boolean {
        val current = currentItemBeingDownloaded
        if (current != null && current.groupSecond == groupSecond) return true
        return try {
            val snapshot = synchronized(queueLock) { queue.toList() }
            snapshot.any { it.groupSecond == groupSecond }
        } catch (_: Exception) {
            true
        }
    }

    fun triggerPollNow() {
        // pollNow @Volatile: setel flag; jeda loop polling tidur dalam irisan pendek 100ms.
        // (wait/notify pada Any() tidak tersedia di sistem tipe Kotlin murni.)
        pollNow = true
    }

    /**
     * Memulai sesi transfer otomatis dengan perangkat USB yang dipilih.
     * Menerima [driveFolderId] folder Google Drive tujuan yang dikunci untuk sesi ini.
     */
    fun start(device: UsbDevice, driveFolderId: String? = null): Boolean {
        synchronized(this) {
            if (state != State.IDLE && state != State.CLOSED && state != State.ERROR) {
                FoundationLog.log("SessionOrchestrator.start() diabaikan: status saat ini = $state")
                return false
            }
            if (!running.compareAndSet(false, true)) {
                return false
            }
            endedByCablePull = false
            sisaKameraCount = 0
            groupInstanceSlots.clear()
            groupKeyByIdentity.clear()
            canonicalBaseByGroup.clear()
            detectedGroupSizes.clear()
            insertedGroupCounts.clear()
            groupDetectedChangedAt.clear()
            groupReadyCandidates.clear()
            downloadAttempts.clear()
            deadIdentities.clear()
            identityHandles.clear()

            if (!driveFolderId.isNullOrEmpty()) {
                targetDriveFolderId = driveFolderId
            }

            isClosing = false
            pollNow = false

            // Reset awal sesi (hanya di sini koleksi dibersihkan)
            knownHandles.clear()
            synchronized(queueLock) {
                queue.clear()
            }
            doneItems.clear()
            failedItems.clear()
            orphanedItems.clear()
            sessionIdentities.clear()
            baselineIdentities.clear()
            skippedBaselineIdentities.clear()
            detectedGroupSeconds.clear()
            checkedOutGroups.clear()
            currentItemBeingDownloaded = null

            statistics = Statistics()

            val thread = Thread({
                runOrchestrator(device)
            }, "orchestrator-main")
            thread.isDaemon = true
            orchestratorThread = thread
            thread.start()
            return true
        }
    }

    /**
     * Mengakhiri sesi secara sopan (CLOSING -> tuntaskan siklus aktif -> TEARDOWN -> CLOSED).
     */
    /** Penanda sesi berakhir karena cabut kabel paksa (semantik user, 9 Nov 2026). */
    @Volatile
    private var endedByCablePull = false

    /** Dipanggil jalur DETACHED USB: sesi kamera berakhir & dibuang. */
    fun stopForCablePull() {
        endedByCablePull = true
        stop()
    }

    fun stop() {
        if (!running.get() && (state == State.IDLE || state == State.CLOSED)) {
            return
        }
        isClosing = true
        if (state != State.CLOSING && state != State.CLOSED) {
            updateState(State.CLOSING)
        }
        pollNow = true
    }

    private fun updateState(newState: State) {
        state = newState
        try {
            listener?.onStateChanged(newState)
        } catch (e: Exception) {
            FoundationLog.error("Error pada callback listener.onStateChanged", e)
        }
    }

    private fun updateStats(transform: Statistics.() -> Statistics) {
        val updated = statistics.transform()
        statistics = updated
        try {
            listener?.onStatisticsUpdated(updated)
        } catch (e: Exception) {
            FoundationLog.error("Error pada callback listener.onStatisticsUpdated", e)
        }
    }

    private fun canonicalIdentity(filename: String, captureDateString: String, sizeBytes: Long): String {
        return "$filename|$captureDateString|$sizeBytes"
    }

    private fun handleDownloadFailureOrOrphan(identity: String, handleToRelease: Int?) {
        val prevHandle = identityHandles[identity]
        val nextAttempts = (downloadAttempts[identity] ?: 0) + 1
        downloadAttempts[identity] = nextAttempts
        if (nextAttempts >= 3) {
            deadIdentities.add(identity)
            FoundationLog.error("[ANTRE] LEWAT-BATAS-COBA $identity — dibiarkan di kamera (salinan kartu aman)")
            val kunci = groupKeyByIdentity.remove(identity)
            if (kunci != null) {
                val currentDetected = detectedGroupSizes[kunci] ?: 0
                val minTarget = insertedGroupCounts[kunci] ?: 0
                val newDetected = (currentDetected - 1).coerceAtLeast(minTarget)
                detectedGroupSizes[kunci] = newDetected
                groupDetectedChangedAt[kunci] = System.currentTimeMillis()
                if (newDetected > 0 && minTarget >= newDetected) {
                    groupReadyCandidates.add(kunci)
                }
                FoundationLog.log(
                    "[ANTRE] Grup $kunci: target dikurangi — anggota $identity menyerah sesudah 3x; anggota lain dinilai dengan yang ada"
                )
            }
        } else {
            sessionIdentities.remove(identity)
            if (handleToRelease != null) {
                knownHandles.remove(handleToRelease)
            }
            if (prevHandle != null) {
                knownHandles.remove(prevHandle)
            }
        }
    }

    private fun runOrchestrator(device: UsbDevice) {
        var idSesi: String? = null
        var okupansiAwal = 0

        try {
            // =================================================================
            // 1. CONNECTING
            // =================================================================
            updateState(State.CONNECTING)
            FoundationLog.log("SessionOrchestrator: Membuka sesi PTP ke ${device.deviceName}...")
            val opened = ptpSession.open(device)
            if (!opened || isClosing || ptpSession.isBroken()) {
                FoundationLog.error("SessionOrchestrator: Gagal membuka koneksi PTP atau sesi dibatalkan")
                updateState(State.ERROR)
                cleanupTeardown(null, 0)
                return
            }

            // =================================================================
            // 2. BASELINE (BRS Q3)
            // =================================================================
            updateState(State.BASELINE)
            val devInfo = ptpSession.lastDeviceInfo ?: ptpSession.getDeviceInfo()
            val cameraModel = devInfo?.model ?: "Kamera Fujifilm"
            val cameraSerial = devInfo?.serialNumber ?: "UNKNOWN"

            // Snapshot getObjectHandles storage Still -> GetObjectInfo tiap handle awal -> baselineIdentities
            // Aturan lolos timestamp dua arah + topi okupansi D20E:
            // (a) Jendela lolos: captureSec >= baselineStartSec - 10 DAN captureSec <= baselineStartSec + 90.
            // (b) Topi okupansi: kandidat yang lolos jendela diurutkan captureSec MENURUN (terbaru dulu),
            //     diloloskan paling banyak sebanyak okupansiAwal (angka okupansi D20E); sisanya tetap
            //     menjadi baselineIdentities yang dikecualikan.
            val baselineStartSec = System.currentTimeMillis() / 1000L
            val baselineHandles = ptpSession.getObjectHandles(ptpSession.stillStorageId)
            knownHandles.clear()
            baselineIdentities.clear()
            skippedBaselineIdentities.clear()

            val d20eInitial = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_FREE_SDRAM_IMAGES)
            val okupansiD20E = if (d20eInitial != null && d20eInitial in 0..12) {
                12 - d20eInitial
            } else {
                baselineHandles.size
            }
            okupansiAwal = okupansiD20E

            data class BaselineCandidate(
                val handle: Int,
                val info: PtpSession.ObjectInfoResult,
                val identity: String,
                val captureSec: Long
            )

            val windowCandidates = mutableListOf<BaselineCandidate>()

            for (h in baselineHandles) {
                val info = ptpSession.getObjectInfo(h)
                if (info != null) {
                    val id = canonicalIdentity(info.filename, info.captureDateString, info.sizeBytes)
                    val captureSec = parseCaptureSecond(info.captureDateString)
                    val lolosTimestamp = captureSec != null &&
                        captureSec >= baselineStartSec - BASELINE_GRACE_SECONDS &&
                        captureSec <= baselineStartSec + BASELINE_FUTURE_GRACE_SECONDS
                    if (lolosTimestamp && captureSec != null) {
                        windowCandidates.add(BaselineCandidate(h, info, id, captureSec))
                    } else {
                        knownHandles.add(h)
                        baselineIdentities.add(id)
                    }
                }
            }

            data class BaselineShotInstance(
                val captureDateString: String,
                val members: MutableList<BaselineCandidate> = mutableListOf(),
                val extClasses: MutableSet<String> = mutableSetOf(),
                var maxCaptureSec: Long = Long.MIN_VALUE
            )

            val sortedCandidates = windowCandidates.sortedByDescending { it.captureSec }
            val instancesBySecond = linkedMapOf<String, MutableList<BaselineShotInstance>>()
            for (cand in sortedCandidates) {
                val secKey = cand.info.captureDateString
                val lower = cand.info.filename.lowercase(Locale.US)
                val extClass = when {
                    lower.endsWith(".raf") -> "RAF"
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "JPG"
                    else -> cand.info.filename.substringAfterLast('.', "").uppercase(Locale.US)
                }
                val listForSec = instancesBySecond.getOrPut(secKey) { mutableListOf() }
                val lastInst = listForSec.lastOrNull()
                val targetInst = if (lastInst != null && extClass !in lastInst.extClasses) {
                    lastInst
                } else {
                    BaselineShotInstance(secKey).also { listForSec.add(it) }
                }
                targetInst.extClasses.add(extClass)
                targetInst.members.add(cand)
                if (cand.captureSec > targetInst.maxCaptureSec) {
                    targetInst.maxCaptureSec = cand.captureSec
                }
            }

            val sortedInstances = instancesBySecond.values.flatten().sortedByDescending { it.maxCaptureSec }
            val allowedInstanceCount = okupansiD20E.coerceAtLeast(0)
            for ((instIdx, inst) in sortedInstances.withIndex()) {
                if (instIdx < allowedInstanceCount) {
                    for (cand in inst.members) {
                        FoundationLog.log(
                            "[BASELINE] LOLOS-TIMESTAMP ${cand.identity} (jepret ${cand.info.captureDateString}, " +
                                "selisih ${baselineStartSec - cand.captureSec}s dalam [-${BASELINE_GRACE_SECONDS}s..+${BASELINE_FUTURE_GRACE_SECONDS}s]) " +
                                "— diperlakukan sebagai foto sesi"
                        )
                        // Handle SENGAJA tidak ditanam ke knownHandles dan identitas tidak
                        // masuk baselineIdentities: polling pertama menemukannya sebagai
                        // foto baru dan mengantrekannya lewat jalur normal.
                    }
                } else {
                    for (cand in inst.members) {
                        knownHandles.add(cand.handle)
                        baselineIdentities.add(cand.identity)
                    }
                }
            }

            okupansiAwal = baselineIdentities.size
            if (d20eInitial != null && d20eInitial in 0..12 && (12 - d20eInitial) != okupansiAwal) {
                FoundationLog.log(
                    "[BASELINE] Catatan: D20E menyiratkan ${12 - d20eInitial} penghuni, terhitung eksklusi $okupansiAwal " +
                        "(selisihnya = lolos timestamp atau tak terenumerasi)"
                )
            }

            // Baca D20C, D211, D212 untuk pencatatan awal
            val d20c = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_MEDIA_RECORD)
            val d211 = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_MEDIA_STATUS)
            val d212 = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_CURRENT_STATE)

            val d20cStr = d20c?.let { "0x" + hex16(it) } ?: "null"
            val d211Str = d211?.let { "0x" + hex16(it) } ?: "null"
            val d212Str = d212?.let { "0x" + hex16(it) } ?: "null"

            FoundationLog.log(
                String.format(
                    Locale.US,
                    "[BASELINE] Selesai: baselineIdentities=%d, knownHandles=%d, D20E=%s (okupansiAwal=%d), D20C=%s, D211=%s, D212=%s",
                    baselineIdentities.size,
                    knownHandles.size,
                    d20eInitial?.toString() ?: "null",
                    okupansiAwal,
                    d20cStr,
                    d211Str,
                    d212Str
                )
            )

            if (okupansiAwal > 0) {
                FoundationLog.log("[BASELINE] buffer tidak kosong: $okupansiAwal slot terisi dari sebelum sesi")
            }
            sisaKameraCount = baselineIdentities.size

            updateStats {
                copy(
                    cameraModel = cameraModel,
                    cameraSerial = cameraSerial,
                    slotRuangTunggu = d20eInitial ?: 12
                )
            }

            if (isClosing || ptpSession.isBroken()) {
                cleanupTeardown(null, okupansiAwal)
                return
            }

            // =================================================================
            // 3. MONITORING
            // =================================================================
            updateState(State.MONITORING)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            idSesi = stamp
            currentSessionId = stamp
            val folderToLock = targetDriveFolderId ?: "root"
            db?.insertSession(id = stamp, driveFolderId = folderToLock)
            FoundationLog.log("[SESI] Sesi $stamp mencatat folder tujuan terkunci: $folderToLock")

            val capturesBaseDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "captures")
            val sessionDir = File(capturesBaseDir, stamp)
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }
            val indexFile = File(sessionDir, "index.jsonl")
            FoundationLog.log("SessionOrchestrator: Memulai pemantauan antrean (idSesi=$stamp, folder=${sessionDir.absolutePath})")

            var grupTerdeteksi = 0
            var grupCheckout = 0
            var lastPolledHandleSet: Set<Int>? = null

            fun discoverNewItems(handles: IntArray) {
                // Log daftar handle mentah HANYA saat set handle berubah (anti-spam)
                val currentSet = handles.toSet()
                if (currentSet != lastPolledHandleSet) {
                    lastPolledHandleSet = currentSet
                    val handleListStr = handles.joinToString(", ") { "0x" + hex32(it) }
                    FoundationLog.log("[POLL] handle=[$handleListStr]")
                }

                for (h in handles) {
                    if (h in knownHandles) continue

                    val info = ptpSession.getObjectInfo(h) ?: continue
                    // Set knownHandles HANYA ditambah saat ObjectInfo berhasil dibaca, TIDAK PERNAH di-clear di tengah sesi
                    knownHandles.add(h)

                    val id = canonicalIdentity(info.filename, info.captureDateString, info.sizeBytes)
                    if (id in deadIdentities) {
                        continue
                    }

                    if (id in baselineIdentities) {
                        if (skippedBaselineIdentities.add(id)) {
                            FoundationLog.log("[ANTRE] lewati-baseline: $id")
                        }
                        continue
                    }

                    if (id in sessionIdentities) {
                        continue
                    }

                    val existingGroupKey = groupKeyByIdentity[id]
                    val assignedGroupKey = if (existingGroupKey != null) {
                        FoundationLog.log("[ANTRE] Ditemukan ulang (coba-lagi) $id -> grup $existingGroupKey")
                        existingGroupKey
                    } else {
                        val key = assignGroupKey(info.captureDateString, info.filename)
                        groupKeyByIdentity[id] = key
                        key
                    }
                    val nowMs = System.currentTimeMillis()

                    // Tambah item QUEUED ke antrean FIFO
                    val queueItem = QueueItem(
                        identity = id,
                        filename = info.filename,
                        captureDateString = info.captureDateString,
                        sizeBytes = info.sizeBytes,
                        formatCode = info.formatCode,
                        groupSecond = assignedGroupKey,
                        state = ItemState.QUEUED,
                        firstSeenElapsedMs = nowMs
                    )
                    sessionIdentities.add(id)
                    identityHandles[id] = h
                    if (existingGroupKey == null) {
                        detectedGroupSizes.merge(queueItem.groupSecond, 1, Int::plus)
                        groupDetectedChangedAt[queueItem.groupSecond] = System.currentTimeMillis()
                    }
                    synchronized(queueLock) {
                        queue.addLast(queueItem)
                    }
                    FoundationLog.log("[ANTRE] + $id")

                    if (detectedGroupSeconds.add(queueItem.groupSecond)) {
                        grupTerdeteksi++
                    }
                }
            }

            fun evaluateReadyGroupCandidates() {
                if (groupReadyCandidates.isEmpty()) return
                val nowMs = System.currentTimeMillis()
                val iter = groupReadyCandidates.iterator()
                while (iter.hasNext()) {
                    val grup = iter.next()
                    val detected = detectedGroupSizes[grup] ?: 0
                    val inserted = insertedGroupCounts[grup] ?: 0
                    val changedAt = groupDetectedChangedAt[grup] ?: 0L
                    if (detected > 0 &&
                        inserted >= detected &&
                        (nowMs - changedAt) >= 1200L &&
                        !isGroupInFlight(idSesi, grup)
                    ) {
                        cullingGate?.markGroupReady(idSesi, grup)
                        iter.remove()
                        FoundationLog.log("[CULLING] Grup $grup stabil ($inserted/$detected anggota) — ditandai siap")
                        cullingGate?.kick()
                    }
                }
            }

            while (!isClosing && ptpSession.isConnected()) {
                // Buang Sisa Kamera: eksekusi permintaan UI di sela antrean, di
                // thread ini sendiri. Resolusi handle SEGAR per identitas —
                // handle bergeser sesudah tiap DeleteObject.
                if (buangSisaRequested) {
                    buangSisaRequested = false
                    if (baselineIdentities.isEmpty()) {
                        FoundationLog.log("[SISA] Tidak ada sisa kamera untuk dibuang")
                    } else {
                        var dibuang = 0
                        for (idSisa in baselineIdentities.toList()) {
                            val captureDatePart = idSisa.split('|').getOrNull(1) ?: ""
                            val captureSec = parseCaptureSecond(captureDatePart)
                            if (captureSec != null && captureSec >= baselineStartSec - BUANG_SISA_SAFE_MARGIN_SECONDS) {
                                FoundationLog.log("[SISA] LEWAT-AMAN $idSisa — terlalu dekat dengan awal sesi, tidak dibuang")
                                continue
                            }

                            val handlesNow = ptpSession.getObjectHandles(ptpSession.stillStorageId)
                            var targetHandle: Int? = null
                            for (h in handlesNow) {
                                val inf = ptpSession.getObjectInfo(h) ?: continue
                                if (canonicalIdentity(inf.filename, inf.captureDateString, inf.sizeBytes) == idSisa) {
                                    targetHandle = h
                                    break
                                }
                            }
                            if (targetHandle != null && ptpSession.deleteObject(targetHandle)) {
                                baselineIdentities.remove(idSisa)
                                dibuang++
                                FoundationLog.log("[SISA] BUANG-SISA $idSisa — slot dibebaskan tanpa unduh/unggah (salinan kartu tidak tersentuh)")
                            } else {
                                FoundationLog.log("[SISA] Gagal membuang $idSisa (handle tak ditemukan atau DeleteObject gagal)")
                            }
                        }
                        okupansiAwal = baselineIdentities.size
                        sisaKameraCount = baselineIdentities.size
                        val d20eNow = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_FREE_SDRAM_IMAGES)
                        if (d20eNow != null) {
                            updateStats { copy(slotRuangTunggu = d20eNow) }
                        }
                        FoundationLog.log("[SISA] Selesai: $dibuang slot dibebaskan; sisa eksklusi kini ${baselineIdentities.size}")
                    }
                }

                // Polling enumerasi handle saat ini
                val polledHandles = ptpSession.getObjectHandles(ptpSession.stillStorageId)
                discoverNewItems(polledHandles)
                evaluateReadyGroupCandidates()

                // Bila antrean kosong, jeda polling 1500ms dalam irisan 100ms
                if (synchronized(queueLock) { queue.isEmpty() }) {
                    var waitedMs = 0L
                    while (waitedMs < 1500 && !pollNow && !isClosing && synchronized(queueLock) { queue.isEmpty() }) {
                        try {
                            Thread.sleep(100)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        waitedMs += 100
                        evaluateReadyGroupCandidates()
                    }
                    pollNow = false
                    continue
                }

                // =============================================================
                // 4. Pekerja penguras serial (di thread orkestrator yang sama)
                // =============================================================
                val item = synchronized(queueLock) { queue.removeFirst() }
                item.state = ItemState.DOWNLOADING
                currentItemBeingDownloaded = item

                // Enumerasi segar per item sebelum unduh
                val freshHandles = ptpSession.getObjectHandles(ptpSession.stillStorageId)
                discoverNewItems(freshHandles)

                // Cari handle yang ObjectInfo segarnya cocok identity item
                var targetHandle: Int? = null
                var freshInfo: PtpSession.ObjectInfoResult? = null

                for (candHandle in freshHandles) {
                    val cand = ptpSession.getObjectInfo(candHandle)
                    if (cand != null) {
                        knownHandles.add(candHandle)
                        val candId = canonicalIdentity(cand.filename, cand.captureDateString, cand.sizeBytes)
                        if (candId == item.identity) {
                            targetHandle = candHandle
                            freshInfo = cand
                            identityHandles[item.identity] = candHandle
                            break
                        }
                    }
                }

                if (targetHandle == null || freshInfo == null) {
                    // Item tidak ketemu handle-nya = ORPHANED dengan log keras
                    item.state = ItemState.ORPHANED
                    orphanedItems.add(item)
                    currentItemBeingDownloaded = null
                    FoundationLog.error("[ANTRE] ORPHANED ${item.identity}")

                    appendIndexJsonl(
                        indexFile = indexFile,
                        serial = cameraSerial,
                        captureDate = item.captureDateString,
                        filename = item.filename,
                        size = item.sizeBytes,
                        md5 = "",
                        handle = 0,
                        groupCaptureSecond = item.groupSecond,
                        status = "ORPHANED"
                    )
                    updateStats { copy(gagalCount = gagalCount + 1) }
                    handleDownloadFailureOrOrphan(item.identity, targetHandle)
                    continue
                }

                // Unduh streaming via PtpSession langsung ke berkas.
                // Skema nama jepret aplikasi (keputusan user, 10 Nov 2026):
                // setiap JEPRETAN (grup) menerima nomor urut per TANGGAL dari
                // aplikasi — IMG_<yyyyMMdd>_<NNN> — dan semua berkasnya memakai
                // nama dasar yang sama (IMG_20261110_007.raf + .jpg). Paritas
                // mutlak di segala campuran format (JPG-only / RAW-only /
                // RAW+JPG berganti di tengah sesi) karena penomoran per
                // jepretan, bukan per berkas kamera. Penghitung persisten per
                // tanggal (SharedPreferences) sehingga sesi kedua di hari yang
                // sama MELANJUTKAN nomor, tidak mulai dari 001 lagi — nama
                // tidak pernah bentrok antar-sesi. Identitas kamera tetap utuh
                // di log/indeks; yang diganti hanya nama simpan + nama Drive.
                var groupBase = canonicalBaseByGroup[item.groupSecond]
                if (groupBase == null) {
                    val datePart = item.captureDateString.substringBefore('T').take(8)
                        .ifEmpty { item.groupSecond.substringBefore('#').substringBefore('T').take(8) }
                        .ifEmpty { idSesi?.take(8) ?: "00000000" }
                    val seq = nextImgSeq(datePart)
                    groupBase = "IMG_" + datePart + "_" + String.format("%03d", seq)
                    canonicalBaseByGroup[item.groupSecond] = groupBase
                    FoundationLog.log("[NAMA] Jepret $groupBase untuk grup ${item.groupSecond} (berawal dari ${freshInfo.filename})")
                }
                val ext = freshInfo.filename.substringAfterLast('.', "")
                var candidate = if (ext.isEmpty()) groupBase else "$groupBase.$ext"
                var dupSuffix = 2
                while (candidate != freshInfo.filename && File(sessionDir, candidate).exists()) {
                    candidate = if (ext.isEmpty()) groupBase + "_" + dupSuffix else groupBase + "_" + dupSuffix + "." + ext
                    dupSuffix++
                }
                if (candidate != freshInfo.filename) {
                    FoundationLog.log("[NAMA] ${freshInfo.filename} disimpan sebagai $candidate")
                }
                val effectiveName = candidate
                val destinationFile = File(sessionDir, effectiveName)
                val dlResult = ptpSession.streamGetObjectToFile(
                    handle = targetHandle,
                    destinationFile = destinationFile,
                    expectedSize = freshInfo.sizeBytes
                )

                val isExact = (dlResult != null && dlResult.success && dlResult.sizeBytes > 0L && dlResult.sizeBytes == freshInfo.sizeBytes)
                if (isExact) {
                    FoundationLog.log(
                        String.format(
                            Locale.US,
                            "[UNDUH] SUKSES: %s -> %s (%d B, md5=%s, %.2f MB/s)",
                            item.identity,
                            destinationFile.name,
                            dlResult.sizeBytes,
                            dlResult.md5Hex,
                            dlResult.mbPerSec
                        )
                    )

                    appendIndexJsonl(
                        indexFile = indexFile,
                        serial = cameraSerial,
                        captureDate = freshInfo.captureDateString,
                        filename = freshInfo.filename,
                        size = freshInfo.sizeBytes,
                        md5 = dlResult.md5Hex,
                        handle = targetHandle,
                        groupCaptureSecond = item.groupSecond,
                        status = "DOWNLOADED"
                    )

                    // Titik penulisan DOWNLOADED: masukkan item ke antrean TransferInDb berstatus CULLING_PENDING dan picu CullingGate
                    var allowCheckout = false
                    if (idSesi != null && db != null) {
                        val inserted = db?.insertItemIgnore(
                            sessionId = idSesi,
                            identity = item.identity,
                            filename = freshInfo.filename,
                            captureDate = freshInfo.captureDateString,
                            sizeBytes = freshInfo.sizeBytes,
                            md5 = dlResult.md5Hex,
                            localPath = destinationFile.absolutePath,
                            driveName = destinationFile.name,
                            groupSecond = item.groupSecond,
                            statusAwal = ItemStatus.CULLING_PENDING,
                            culling = CullingVerdict.PENDING
                        ) ?: false
                        if (inserted) {
                            FoundationLog.log("[CULLING] Item dimasukkan ke antrean DB (CULLING_PENDING): ${freshInfo.filename} (${freshInfo.sizeBytes} B)")
                            allowCheckout = true
                        } else {
                            try {
                                if (destinationFile.exists()) {
                                    destinationFile.delete()
                                }
                                val previewByBase = File(destinationFile.parentFile, "${destinationFile.nameWithoutExtension}.preview.jpg")
                                if (previewByBase.exists()) {
                                    previewByBase.delete()
                                }
                                val previewByName = File(destinationFile.parentFile, "${destinationFile.name}.preview.jpg")
                                if (previewByName.exists()) {
                                    previewByName.delete()
                                }
                            } catch (_: Exception) {
                            }
                            FoundationLog.log("[ANTRE] Berkas duplikat lokal dihapus: ${destinationFile.name} (baris DB lama yang dipakai)")

                            val oldRow = db?.findItemByIdentity(item.identity)
                            val oldStatus = oldRow?.status
                            if (oldStatus == ItemStatus.DONE ||
                                oldStatus == ItemStatus.QUEUED ||
                                oldStatus == ItemStatus.UPLOADING ||
                                oldStatus == ItemStatus.VERIFYING ||
                                oldStatus == ItemStatus.CULLING_PENDING
                            ) {
                                allowCheckout = true
                            } else {
                                FoundationLog.error(
                                    "[ANTRE] DUPLIKAT-IDENTITAS ${item.identity} — baris lama berstatus ${oldStatus ?: "TIDAK_DITEMUKAN"}; foto DIBIARKAN di kamera, tidak di-checkout"
                                )
                                allowCheckout = false
                            }
                        }

                        // Kelengkapan grup: anggota terhitung masuk insertedGroupCounts;
                        // bila insertedCount >= detectedCount, grup dimasukkan ke kandidat siap (aturan tenang).
                        val insertedCount = insertedGroupCounts.merge(item.groupSecond, 1, Int::plus) ?: 1
                        val detectedCount = detectedGroupSizes[item.groupSecond] ?: 0
                        if (detectedCount > 0 && insertedCount >= detectedCount) {
                            groupReadyCandidates.add(item.groupSecond)
                        }
                        cullingGate?.kick()
                    }

                    updateStats {
                        copy(
                            berkasSelesai = berkasSelesai + 1,
                            totalBytesDownloaded = totalBytesDownloaded + dlResult.sizeBytes,
                            lastDownloadMbPerSec = dlResult.mbPerSec
                        )
                    }

                    // Checkout DeleteObject hanya berjalan pada jalur inserted==true atau terhitung-duplikat-aman
                    if (allowCheckout) {
                        var delOk = ptpSession.deleteObject(targetHandle)
                        if (!delOk) {
                            FoundationLog.error("[CHECKOUT] DeleteObject GAGAL untuk ${item.identity} — mencoba sekali lagi")
                            try {
                                Thread.sleep(300)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                            delOk = ptpSession.deleteObject(targetHandle)
                        }

                        item.state = ItemState.DONE
                        doneItems.add(item)

                        if (delOk) {
                            FoundationLog.log("[CHECKOUT] ${item.identity}")

                            // Pengelompokan: grupCheckout HANYA bertambah bila hasil akhir deleteObject true
                            val siblingInFlight = synchronized(queueLock) { queue.any { it.groupSecond == item.groupSecond } } ||
                                (currentItemBeingDownloaded?.groupSecond == item.groupSecond && currentItemBeingDownloaded !== item)
                            if (!siblingInFlight && checkedOutGroups.add(item.groupSecond)) {
                                grupCheckout++
                                updateStats { copy(grupSelesai = grupCheckout) }
                                cullingGate?.kick()
                            }
                        } else {
                            FoundationLog.error(
                                "[CHECKOUT] DeleteObject TETAP GAGAL untuk ${item.identity} — slot mungkin belum bebas tetapi berkas sudah aman di tablet/DB"
                            )
                        }
                    } else {
                        item.state = ItemState.DONE
                        doneItems.add(item)
                    }
                    currentItemBeingDownloaded = null
                    evaluateReadyGroupCandidates()

                    // [SLOT] tetap log advisori dengan rumus yang sama: okupansiAwal + grupTerdeteksi - grupCheckout
                    val d20e = ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_FREE_SDRAM_IMAGES) ?: -1
                    val harapanD20E = 12 - (okupansiAwal + grupTerdeteksi - grupCheckout)
                    if (d20e == harapanD20E) {
                        FoundationLog.log(
                            "[SLOT] D20E=$d20e cocok terhadap harapan ($harapanD20E) [okupansiAwal=$okupansiAwal, terdeteksi=$grupTerdeteksi, checkout=$grupCheckout]"
                        )
                    } else {
                        FoundationLog.log(
                            "[SLOT] TIDAK-COCOK: D20E=$d20e vs harapan=$harapanD20E [okupansiAwal=$okupansiAwal, terdeteksi=$grupTerdeteksi, checkout=$grupCheckout]"
                        )
                    }

                    if (d20e >= 0) {
                        updateStats { copy(slotRuangTunggu = d20e) }
                    }
                } else {
                    // Gagal unduh (termasuk 0-byte): item FAILED + index.jsonl FAILED, TANPA delete
                    item.state = ItemState.FAILED
                    failedItems.add(item)
                    currentItemBeingDownloaded = null
                    FoundationLog.error(
                        "[UNDUH] GAGAL: ${item.identity} -> ${destinationFile.name} (sukses=${dlResult?.success}, byte=${dlResult?.sizeBytes}/${freshInfo.sizeBytes})"
                    )

                    // Berkas parsial sisa unduhan gagal (mis. kabel tercabut di
                    // tengah unduh) dibuang — bukan berkas utuh, tidak akan
                    // pernah diproses, dan sapuan akhir sesi hanya menyentuh
                    // item DONE.
                    try {
                        if (destinationFile.exists() && destinationFile.delete()) {
                            FoundationLog.log("[UNDUH] Berkas parsial dihapus: ${destinationFile.name}")
                        }
                    } catch (_: Exception) {
                    }

                    appendIndexJsonl(
                        indexFile = indexFile,
                        serial = cameraSerial,
                        captureDate = freshInfo.captureDateString,
                        filename = freshInfo.filename,
                        size = freshInfo.sizeBytes,
                        md5 = dlResult?.md5Hex ?: "",
                        handle = targetHandle,
                        groupCaptureSecond = item.groupSecond,
                        status = "FAILED"
                    )

                    updateStats { copy(gagalCount = gagalCount + 1) }
                    handleDownloadFailureOrOrphan(item.identity, targetHandle)
                }
            }

            cleanupTeardown(idSesi, okupansiAwal)
        } catch (e: Exception) {
            FoundationLog.error("Exception tak terduga pada thread orkestrator", e)
            updateState(State.ERROR)
            cleanupTeardown(idSesi, okupansiAwal)
        } finally {
            running.set(false)
            try {
                listener?.onStateChanged(state)
            } catch (e: Exception) {
                FoundationLog.error("Error pada callback listener.onStateChanged di akhir teardown", e)
            }
        }
    }

    private fun cleanupTeardown(
        idSesi: String?,
        okupansiAwal: Int
    ) {
        if (state != State.ERROR) {
            updateState(State.CLOSING)
        }

        // Kumpulkan daftar item QUEUED/DOWNLOADING yang belum selesai by identity
        val unfinishedIdentities = mutableListOf<String>()
        val current = currentItemBeingDownloaded
        if (current != null && (current.state == ItemState.DOWNLOADING || current.state == ItemState.QUEUED)) {
            unfinishedIdentities.add(current.identity)
        }
        val queueSnapshot = synchronized(queueLock) { queue.toList() }
        for (item in queueSnapshot) {
            if (item.state == ItemState.QUEUED || item.state == ItemState.DOWNLOADING) {
                unfinishedIdentities.add(item.identity)
            }
        }

        if (unfinishedIdentities.isNotEmpty()) {
            FoundationLog.log("[TEARDOWN] Sisa antrean belum selesai (" + unfinishedIdentities.size + " item): " + unfinishedIdentities.joinToString(", "))
        } else {
            FoundationLog.log("[TEARDOWN] Antrean kosong, tidak ada item tertinggal di antrean")
        }

        // Semantik akhir sesi (keputusan user, 9 Nov 2026): cabut kabel paksa =
        // sesi kamera berakhir & DIBUANG (tidak dilanjutkan saat kabel kembali;
        // colok lagi = sesi baru). Item yang sudah terunduh ke tablet BUKAN
        // bagian sesi kamera — antrean unggah basis data tetap memprosesnya
        // sampai selesai.
        if (endedByCablePull) {
            FoundationLog.log("[SESI] DIBUANG (cabut kabel paksa): sesi kamera dibuang; sisa antrean kamera gugur, item yang sudah terunduh tetap diunggah antrean tablet sampai selesai")
        }

        // Cek silang D20E akhir apa adanya
        val d20eFinal = try {
            ptpSession.getDevicePropValueAsInt(PtpSession.PROP_FUJI_FREE_SDRAM_IMAGES)
        } catch (_: Exception) {
            null
        }

        val expectedCleanD20E = 12 - okupansiAwal
        if (d20eFinal != null) {
            if (d20eFinal < expectedCleanD20E) {
                FoundationLog.log("[TEARDOWN] D20E akhir=$d20eFinal — buffer belum kosong (target bersih=$expectedCleanD20E)")
            } else {
                FoundationLog.log("[TEARDOWN] D20E akhir=$d20eFinal — buffer bersih (target bersih=$expectedCleanD20E)")
            }
        } else {
            FoundationLog.log("[TEARDOWN] D20E akhir=null — tidak dapat membaca D20E")
        }

        // Tutup koneksi PtpSession secara sopan
        try {
            ptpSession.closeSession()
        } catch (e: Exception) {
            FoundationLog.error("Error saat closeSession di teardown", e)
        }

        // Ringkasan sesi
        val finalStats = statistics
        val stamp = idSesi ?: currentSessionId ?: "-"
        FoundationLog.log("=== RINGKASAN SESI ($stamp) ===")
        FoundationLog.log(
            String.format(
                Locale.US,
                "Grup selesai: %d, Berkas: %d, Total: %.2f MB, Gagal: %d",
                finalStats.grupSelesai,
                finalStats.berkasSelesai,
                finalStats.totalMbDownloaded,
                finalStats.gagalCount
            )
        )

        // Teardown akhir sesi: beri tahu UploadQueue bahwa sesi ini CLOSED
        val closedSessionId = idSesi ?: currentSessionId
        if (closedSessionId != null) {
            uploadQueue?.onSessionClosed(closedSessionId)
        }

        targetDriveFolderId = null
        updateState(State.CLOSED)
    }

    private fun appendIndexJsonl(
        indexFile: File,
        serial: String,
        captureDate: String,
        filename: String,
        size: Long,
        md5: String,
        handle: Int,
        groupCaptureSecond: String,
        status: String
    ) {
        try {
            val json = buildString {
                append('{')
                append("\"serial\":\"").append(escapeJson(serial)).append("\",")
                append("\"captureDate\":\"").append(escapeJson(captureDate)).append("\",")
                append("\"filename\":\"").append(escapeJson(filename)).append("\",")
                append("\"size\":").append(size).append(',')
                append("\"md5\":\"").append(escapeJson(md5)).append("\",")
                append("\"handle\":").append(handle).append(',')
                append("\"groupCaptureSecond\":\"").append(escapeJson(groupCaptureSecond)).append("\",")
                append("\"status\":\"").append(escapeJson(status)).append("\"")
                append('}')
                append('\n')
            }
            FileWriter(indexFile, true).use { writer ->
                writer.write(json)
                writer.flush()
            }
        } catch (e: Exception) {
            FoundationLog.error("Gagal menulis baris ke index.jsonl", e)
        }
    }

    private fun escapeJson(s: String): String {
        val sb = java.lang.StringBuilder()
        for (i in 0 until s.length) {
            val c = s[i]
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0x00..0x1F) {
                        sb.append(String.format(Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    /** Ubah string waktu tangkap PTP ("yyyyMMdd'T'HHmmss", waktu lokal kamera) ke detik epoch; null bila tak terbaca. */
    private fun parseCaptureSecond(captureDateString: String): Long? {
        return try {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(captureDateString)?.let { it.time / 1000L }
        } catch (_: Exception) {
            null
        }
    }

    private fun hex16(v: Int): String = String.format(Locale.US, "%04X", v and 0xFFFF)
    private fun hex32(v: Int): String = String.format(Locale.US, "%08X", v)
}
