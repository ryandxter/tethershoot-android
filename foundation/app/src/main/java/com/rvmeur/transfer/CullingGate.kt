package com.rvmeur.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/* =============================================================================
 * AMBANG PENILAIAN CULLING (Konstanta bernama di kepala berkas)
 * ============================================================================= */

/** Ambang batas bawah rata-rata luminans (di bawah ini = potensi underexposure parah). */
const val CULL_THRESHOLD_MEAN_LUMA_LOW = 15.0f

/** Ambang kerapatan lantai hitam (persentase piksel di bin 0..3 >= 90%). */
const val CULL_THRESHOLD_BLACK_FLOOR_DENSITY = 0.90f

/** Ambang bayangan terpotong pekat (persentase piksel di bin 0..15 >= 85%). */
const val CULL_THRESHOLD_SHADOW_CLIP_PERCENT = 0.85f

/** Ambang batas atas rata-rata luminans (di atas ini = potensi highlight jebol). */
const val CULL_THRESHOLD_MEAN_LUMA_HIGH = 230.0f

/** Ambang saturasi sorotan ekstrem (persentase piksel di bin 248..255 >= 20%). */
const val CULL_THRESHOLD_HIGHLIGHT_SAT_PERCENT = 0.20f

/** Ambang skor varians Laplacian global konservatif (di bawah ini = foto buram/goyang). */
const val CULL_THRESHOLD_BLUR_GLOBAL = 60.0f

/** Jumlah maksimal wajah yang dicari oleh android.media.FaceDetector. */
const val CULL_MAX_DETECTED_FACES = 5

/** Rasio lebar wajah minimum terhadap lebar gambar (>= 10%) agar dinilai sebagai wajah signifikan. */
const val CULL_THRESHOLD_FACE_SIGNIFICANT_RATIO = 0.10f

/** Lantai absolut varians Laplacian pada area wajah (di bawah ini = wajah buram berat). */
const val CULL_THRESHOLD_FACE_BLUR_ABSOLUTE_FLOOR = 35.0f

/** Rasio skor ketajaman wajah terhadap ketajaman global (< 40% = wajah out-of-focus relatif terhadap latar). */
const val CULL_THRESHOLD_FACE_BLUR_GLOBAL_RATIO = 0.40f

/** Target lebar pratinjau bitmap untuk analisis culling (±800px). */
const val CULL_TARGET_PREVIEW_WIDTH = 800

/**
 * Gerbang culling asinkron berbasis thread tunggal 'culling-gate' (Stage 4 Gelombang 1).
 *
 * Menerapkan:
 * - Thread tunggal 'culling-gate' dengan pola kick + tidur irisan tanpa wait/notify pada Any().
 * - Penilaian per GRUP foto (pasangan RAW+JPG dinilai dari JPG-nya; RAF solo dari pratinjau tertanamnya).
 * - Penilaian hanya atas grup yang sudah LENGKAP diunduh (tercantol pada titik penyelesaian grup
 *   SessionOrchestrator, dengan fallback pemindaian grup pending tanpa transfer aktif).
 * - Tiga dimensi evaluasi:
 *     1. Exposure: underexposure parah (misfire blitz) atau highlight blowout.
 *     2. Blur global: varians operator Laplacian atas pratinjau grayscale lebar ±800px.
 *     3. Wajah: android.media.FaceDetector (framework murni) — catat metadata wajah dan HOLD
 *        hanya bila ada wajah signifikan yang wilayahnya buram.
 * - Log wajib [CULLING] untuk setiap penilaian dengan nilai terukur lengkap.
 * - Transisi status:
 *     PASS -> item grup menjadi QUEUED + culling=PASS + kick UploadQueue.
 *     HOLD -> item grup menjadi CULLING_HOLD + cullingDetail alasan bahasa manusia.
 *     Pratinjau tak terbaca -> PASS + catatan.
 */
class CullingGate(
    private val context: Context,
    private val db: TransferInDb,
    private val uploadQueue: UploadQueue
) {

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var kicked: Boolean = false

    private var workerThread: Thread? = null

    // Set grup yang secara eksplisit ditandai siap dinilai oleh orkestrator (sessionId to groupSecond)
    private val readyGroups = ConcurrentHashMap.newKeySet<Pair<String, String>>()

    /** Grup yang sudah menerima vonis pada proses ini — tidak dinilai ulang. */
    private val evaluatedGroups = ConcurrentHashMap.newKeySet<Pair<String, String>>()

    // Referensi ke SessionOrchestrator untuk memeriksa transfer yang sedang berjalan
    var orchestratorRef: (() -> SessionOrchestrator?)? = null

    /**
     * Memulai thread pekerja gerbang culling.
     */
    fun start() {
        if (running) return
        running = true
        kicked = false

        workerThread = Thread({
            FoundationLog.log("[CULLING] Thread gerbang 'culling-gate' dimulai")
            runGateLoop()
            FoundationLog.log("[CULLING] Thread gerbang 'culling-gate' berhenti")
        }, "culling-gate").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Menghentikan thread pekerja secara aman.
     */
    fun stop() {
        running = false
        kick()
        try {
            workerThread?.join(1000)
        } catch (_: Exception) {
        }
        workerThread = null
    }

    /**
     * Membangunkan thread gerbang dari tidur irisan sesegera mungkin.
     */
    fun kick() {
        kicked = true
    }

    /**
     * Menandai bahwa satu grup telah lengkap diunduh oleh orkestrator dan siap dinilai.
     */
    fun markGroupReady(sessionId: String, groupSecond: String) {
        readyGroups.add(sessionId to groupSecond)
        kick()
    }

    /**
     * Loop utama thread culling-gate.
     */
    private fun runGateLoop() {
        while (running) {
            val pendingGroups = db.getPendingCullingGroups()
            if (pendingGroups.isEmpty()) {
                sleepSlices(2000L)
                continue
            }

            // Cari grup pending yang sudah lengkap (sudah selesai unduh seluruh anggotanya)
            var targetGroup: TransferInDb.PendingCullingGroup? = null
            for (candidate in pendingGroups) {
                // Satu grup, satu vonis — grup yang sudah pernah dinilai tidak
                // dinilai ulang SELAMA sesinya hidup (vonis ganda terbukti
                // memisahkan pasangan: anggota pertama lolos, sisanya tertahan).
                // Pengecualian sapuan akhir: bila sesinya sudah DITUTUP dan masih
                // ada anggota menggantung (terdeteksi sangat terlambat), grup
                // boleh dinilai sekali lagi untuk anggota sisa itu saja.
                if (evaluatedGroups.contains(candidate.sessionId to candidate.groupSecond) &&
                    !db.isSessionClosed(candidate.sessionId)
                ) {
                    continue
                }
                if (isGroupReadyForEvaluation(candidate.sessionId, candidate.groupSecond)) {
                    targetGroup = candidate
                    break
                }
            }

            if (targetGroup == null) {
                // Ada item CULLING_PENDING tetapi berkas saudaranya masih dalam antrean unduh kamera
                sleepSlices(500L)
                continue
            }

            // Nilai grup yang siap
            try {
                evaluateGroup(targetGroup.sessionId, targetGroup.groupSecond)
            } catch (t: Throwable) {
                FoundationLog.error("[CULLING] Eksepsi saat menilai grup ${targetGroup.sessionId}/${targetGroup.groupSecond}", t)
                // Fallback aman: jika ada eksepsi tidak terduga, loloskan agar antrean tidak macet
                db.setGroupCullingVerdict(
                    sessionId = targetGroup.sessionId,
                    groupSecond = targetGroup.groupSecond,
                    newStatus = ItemStatus.QUEUED,
                    cullingVerdict = CullingVerdict.PASS,
                    cullingDetail = "Lolos otomatis: kegagalan evaluasi internal (${t.message ?: "eksepsi"})"
                )
                uploadQueue.kick()
            } finally {
                evaluatedGroups.add(targetGroup.sessionId to targetGroup.groupSecond)
                readyGroups.remove(targetGroup.sessionId to targetGroup.groupSecond)
            }
        }
    }

    /**
     * Memeriksa apakah suatu grup sudah selesai diunduh sepenuhnya dan siap dinilai.
     */
    private fun isGroupReadyForEvaluation(sessionId: String, groupSecond: String): Boolean {
        // 1. Apakah orkestrator secara eksplisit menandai grup ini siap (seluruh unduh selesai)?
        if (readyGroups.contains(sessionId to groupSecond)) {
            val orch = orchestratorRef?.invoke()
            // Gagal-tertutup: tanpa referensi orkestrator, kesiapan tidak bisa
            // diverifikasi — JANGAN menilai. (Null pernah berarti "siap" dan
            // membiarkan gerbang menilai grup separuh selama beberapa versi.)
            if (orch == null) {
                return false
            }
            if (orch.isGroupInFlight(sessionId, groupSecond)) {
                return false
            }
            return true
        }

        // 2. Fallback: periksa status sesi di DB — jika sesi sudah CLOSED, grup pasti tidak berjalan lagi
        if (db.isSessionClosed(sessionId)) {
            return true
        }

        // 3. Bila tidak ada sesi kamera yang hidup, grup ini tidak akan pernah
        // bertambah anggota lagi — nilai dengan anggota yang ada. (Tebakan lama
        // "tidak sedang diunduh = lengkap" DIBUANG: itulah lubang yang menilai
        // grup separuh, lalu menilai ulang sisanya dengan vonis berbeda.)
        val orch = orchestratorRef?.invoke()
        if (orch != null && (orch.state == SessionOrchestrator.State.IDLE || orch.state == SessionOrchestrator.State.CLOSED)) {
            return true
        }

        return false
    }

    /**
     * Menilai satu grup tangkapan lengkap (exposure, blur global, dan wajah).
     */
    private fun evaluateGroup(sessionId: String, groupSecond: String) {
        val items = db.getItemsInGroup(sessionId, groupSecond)
        if (items.isEmpty()) {
            return
        }

        // 1. Dapatkan berkas pratinjau untuk penilaian:
        // Prioritas A: berkas JPG/JPEG bila grup memiliki pasangan JPG.
        // Prioritas B: ekstrak pratinjau JPEG dari RAF solo dan simpan sebagai <nama>.preview.jpg.
        val previewBitmap = loadOrExtractPreviewBitmap(items)

        if (previewBitmap == null) {
            // Pratinjau tak terbaca: PASS dengan catatan log (BRS / Spec: "Gerbang tak menahan apa yang tak bisa ia lihat")
            val note = "Lolos: pratinjau tidak dapat terbaca atau tidak ditemukan"
            FoundationLog.log("[CULLING] grup=$sessionId/$groupSecond pratinjau tak terbaca -> PASS ($note)")
            db.setGroupCullingVerdict(
                sessionId = sessionId,
                groupSecond = groupSecond,
                newStatus = ItemStatus.QUEUED,
                cullingVerdict = CullingVerdict.PASS,
                cullingDetail = note
            )
            uploadQueue.kick()
            return
        }

        try {
            // 2. Analisis Dimensi 1: Exposure (keterpaparan)
            val exposureMetrics = ExposureAnalyzer.analyzeBitmap(previewBitmap)

            // 3. Analisis Dimensi 2: Blur Global (Varians Laplacian pada piksel grayscale)
            val width = previewBitmap.width
            val height = previewBitmap.height
            val grayPixels = IntArray(width * height)
            extractGrayscaleLuma(previewBitmap, grayPixels)
            val globalBlurScore = computeLaplacianVariance(grayPixels, width, height, 1, 1, width - 1, height - 1)

            // 4. Analisis Dimensi 3: Wajah (android.media.FaceDetector)
            val faceAnalysis = analyzeFaces(previewBitmap, grayPixels, width, height, globalBlurScore)

            // 5. Penentuan Vonis Culling
            var isHold = false
            var holdReason: String? = null

            // Evaluasi Exposure:
            if (exposureMetrics.meanLuminance < CULL_THRESHOLD_MEAN_LUMA_LOW &&
                (exposureMetrics.blackFloorDensity >= CULL_THRESHOLD_BLACK_FLOOR_DENSITY ||
                    exposureMetrics.shadowClippingPercent >= CULL_THRESHOLD_SHADOW_CLIP_PERCENT)
            ) {
                isHold = true
                holdReason = String.format(
                    Locale.US,
                    "Underexposure berat (rerata luma %.1f, bayangan pekat %.1f%%)",
                    exposureMetrics.meanLuminance,
                    exposureMetrics.shadowClippingPercent * 100f
                )
            } else if (exposureMetrics.meanLuminance > CULL_THRESHOLD_MEAN_LUMA_HIGH &&
                exposureMetrics.highlightSaturationPercent >= CULL_THRESHOLD_HIGHLIGHT_SAT_PERCENT
            ) {
                isHold = true
                holdReason = String.format(
                    Locale.US,
                    "Highlight jebol (rerata luma %.1f, sorotan jenuh %.1f%%)",
                    exposureMetrics.meanLuminance,
                    exposureMetrics.highlightSaturationPercent * 100f
                )
            } else if (globalBlurScore < CULL_THRESHOLD_BLUR_GLOBAL) {
                // Evaluasi Blur Global:
                isHold = true
                holdReason = String.format(
                    Locale.US,
                    "Goyang/blur global (skor ketajaman %.1f < %.1f)",
                    globalBlurScore,
                    CULL_THRESHOLD_BLUR_GLOBAL
                )
            } else if (faceAnalysis.hasSignificantBlurredFace) {
                // Evaluasi Ketajaman Wajah Signifikan:
                isHold = true
                holdReason = faceAnalysis.blurredFaceReason
            }

            val verdictStr = if (isHold) CullingVerdict.HOLD else CullingVerdict.PASS
            val finalDetail = if (isHold) {
                holdReason ?: "Tertahan kriteria culling"
            } else {
                String.format(
                    Locale.US,
                    "Lolos: exposure normal (luma %.1f), fokus tajam (skor %.1f)%s",
                    exposureMetrics.meanLuminance,
                    globalBlurScore,
                    if (faceAnalysis.faceCount > 0) ", wajah: ${faceAnalysis.faceCount}" else ""
                )
            }

            // 6. Log WAJIB [CULLING] berisi nilai terukur lengkap
            FoundationLog.log(
                String.format(
                    Locale.US,
                    "[CULLING] grup=%s/%s nilai: luma=%.1f, bayangan=%.2f%%, sorotan=%.2f%%, blurGlobal=%.1f, wajah=%d(maxRasio=%.2f,skorWajah=%.1f) -> %s (%s)",
                    sessionId,
                    groupSecond,
                    exposureMetrics.meanLuminance,
                    exposureMetrics.shadowClippingPercent * 100f,
                    exposureMetrics.highlightSaturationPercent * 100f,
                    globalBlurScore,
                    faceAnalysis.faceCount,
                    faceAnalysis.maxFaceRatio,
                    faceAnalysis.minFaceBlurScore,
                    verdictStr,
                    finalDetail
                )
            )

            // 7. Terapkan Vonis ke Database
            if (isHold) {
                db.setGroupCullingVerdict(
                    sessionId = sessionId,
                    groupSecond = groupSecond,
                    newStatus = ItemStatus.CULLING_HOLD,
                    cullingVerdict = CullingVerdict.HOLD,
                    cullingDetail = finalDetail
                )
            } else {
                db.setGroupCullingVerdict(
                    sessionId = sessionId,
                    groupSecond = groupSecond,
                    newStatus = ItemStatus.QUEUED,
                    cullingVerdict = CullingVerdict.PASS,
                    cullingDetail = finalDetail
                )
                // Picu antrean unggah karena item baru saja menjadi QUEUED
                uploadQueue.kick()
            }
        } finally {
            try {
                previewBitmap.recycle()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Memuat Bitmap pratinjau yang disub-sample ke lebar ±800px.
     * Menggunakan berkas JPG jika tersedia, atau mengekstrak pratinjau embedded JPEG dari berkas RAF.
     */
    private fun loadOrExtractPreviewBitmap(items: List<TransferItem>): Bitmap? {
        // Cari berkas JPG/JPEG terlebih dahulu
        val jpgItem = items.firstOrNull {
            it.filename.endsWith(".jpg", ignoreCase = true) ||
                it.filename.endsWith(".jpeg", ignoreCase = true)
        }
        if (jpgItem != null) {
            val file = File(jpgItem.localPath)
            if (file.exists() && file.length() > 0L) {
                val bmp = decodeSampledBitmap(file, CULL_TARGET_PREVIEW_WIDTH)
                if (bmp != null) return bmp
            }
        }

        // Jika tidak ada JPG, cari berkas RAF
        val rafItem = items.firstOrNull { it.filename.endsWith(".raf", ignoreCase = true) }
        if (rafItem != null) {
            val rafFile = File(rafItem.localPath)
            if (rafFile.exists() && rafFile.length() > 0L) {
                // Simpan sebagai <nama>.preview.jpg di folder tangkapan sesi
                val previewFile = File(rafFile.parentFile, "${rafFile.nameWithoutExtension}.preview.jpg")
                if (!previewFile.exists() || previewFile.length() == 0L) {
                    val extracted = RafPreviewExtractor.extractAndSavePreview(rafFile, previewFile)
                    if (extracted) {
                        FoundationLog.log("[CULLING] Pratinjau RAF diekstrak: ${previewFile.name} (${previewFile.length()} B)")
                    }
                }
                if (previewFile.exists() && previewFile.length() > 0L) {
                    val bmp = decodeSampledBitmap(previewFile, CULL_TARGET_PREVIEW_WIDTH)
                    if (bmp != null) return bmp
                }
            }
        }

        return null
    }

    /**
     * Membaca berkas gambar dan men-decode Bitmap dengan sub-sampling ukuran lebar ±800px.
     * Mengonversi ke Bitmap.Config.RGB_565 dengan lebar genap demi kompatibilitas FaceDetector.
     */
    private fun decodeSampledBitmap(file: File, targetWidth: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            while (origWidth / (sampleSize * 2) >= targetWidth) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            // FaceDetector Android mewajibkan format RGB_565 dan LEBAR GENAP (even width)
            val isRgb565 = decoded.config == Bitmap.Config.RGB_565
            val isEvenWidth = (decoded.width % 2 == 0)

            if (isRgb565 && isEvenWidth) {
                decoded
            } else {
                val safeWidth = if (decoded.width % 2 != 0) decoded.width - 1 else decoded.width
                val safeHeight = decoded.height
                val safeBmp = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.RGB_565)
                val canvas = android.graphics.Canvas(safeBmp)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                val srcRect = android.graphics.Rect(0, 0, safeWidth, safeHeight)
                canvas.drawBitmap(decoded, srcRect, srcRect, paint)
                decoded.recycle()
                safeBmp
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mengekstrak nilai luminans grayscale per piksel ke dalam larik integer.
     */
    private fun extractGrayscaleLuma(bitmap: Bitmap, outputGray: IntArray) {
        val width = bitmap.width
        val height = bitmap.height
        val rowBuffer = IntArray(width)

        var y = 0
        while (y < height) {
            bitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
            val rowOffset = y * width
            var x = 0
            while (x < width) {
                val pixel = rowBuffer[x]
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                outputGray[rowOffset + x] = (77 * r + 150 * g + 29 * b) ushr 8
                x++
            }
            y++
        }
    }

    /**
     * Menghitung varians dari operator 3x3 Laplacian pada larik piksel grayscale.
     * Kernel Laplacian 4-arah:
     *   [  0,  1,  0 ]
     *   [  1, -4,  1 ]
     *   [  0,  1,  0 ]
     */
    private fun computeLaplacianVariance(
        gray: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Double {
        val safeStartX = Math.max(1, startX)
        val safeStartY = Math.max(1, startY)
        val safeEndX = Math.min(width - 1, endX)
        val safeEndY = Math.min(height - 1, endY)

        if (safeEndX <= safeStartX || safeEndY <= safeStartY) {
            return 0.0
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        var y = safeStartY
        while (y < safeEndY) {
            val yOffset = y * width
            val yPrevOffset = (y - 1) * width
            val yNextOffset = (y + 1) * width
            var x = safeStartX
            while (x < safeEndX) {
                val center = gray[yOffset + x]
                val up = gray[yPrevOffset + x]
                val down = gray[yNextOffset + x]
                val left = gray[yOffset + x - 1]
                val right = gray[yOffset + x + 1]

                val lap = (up + down + left + right - (center shl 2)).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
                x++
            }
            y++
        }

        if (count == 0) return 0.0
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return Math.max(0.0, variance)
    }

    data class FaceAnalysisResult(
        val faceCount: Int,
        val maxFaceRatio: Float,
        val minFaceBlurScore: Double,
        val hasSignificantBlurredFace: Boolean,
        val blurredFaceReason: String?
    )

    /**
     * Menganalisis keberadaan wajah menggunakan android.media.FaceDetector dan
     * menghitung skor ketajaman area wajah jika wajah berukuran cukup besar.
     */
    private fun analyzeFaces(
        bitmap: Bitmap,
        grayPixels: IntArray,
        width: Int,
        height: Int,
        globalBlurScore: Double
    ): FaceAnalysisResult {
        if (width <= 0 || height <= 0 || width % 2 != 0 || bitmap.config != Bitmap.Config.RGB_565) {
            return FaceAnalysisResult(
                faceCount = 0,
                maxFaceRatio = 0.0f,
                minFaceBlurScore = 0.0,
                hasSignificantBlurredFace = false,
                blurredFaceReason = null
            )
        }

        return try {
            val detector = FaceDetector(width, height, CULL_MAX_DETECTED_FACES)
            val faces = arrayOfNulls<FaceDetector.Face>(CULL_MAX_DETECTED_FACES)
            val faceCount = detector.findFaces(bitmap, faces)

            if (faceCount == 0) {
                return FaceAnalysisResult(
                    faceCount = 0,
                    maxFaceRatio = 0.0f,
                    minFaceBlurScore = 0.0,
                    hasSignificantBlurredFace = false,
                    blurredFaceReason = null
                )
            }

            var maxRatio = 0.0f
            var minBlur = Double.MAX_VALUE
            var hasSignificantBlurredFace = false
            var blurredReason: String? = null

            val midPoint = PointF()
            for (i in 0 until faceCount) {
                val face = faces[i] ?: continue
                if (face.confidence() < 0.40f) continue

                val eyesDist = face.eyesDistance()
                face.getMidPoint(midPoint)

                // Estimasi lebar wajah kasaran dari jarak mata (~2x jarak mata)
                val estimatedFaceWidth = eyesDist * 2.0f
                val ratio = estimatedFaceWidth / width.toFloat()
                if (ratio > maxRatio) {
                    maxRatio = ratio
                }

                // Apakah wajah ini cukup besar untuk dinilai ketajamannya?
                if (ratio >= CULL_THRESHOLD_FACE_SIGNIFICANT_RATIO) {
                    val roiLeft = (midPoint.x - eyesDist * 1.1f).toInt().coerceIn(1, width - 2)
                    val roiRight = (midPoint.x + eyesDist * 1.1f).toInt().coerceIn(roiLeft + 2, width - 1)
                    val roiTop = (midPoint.y - eyesDist * 0.9f).toInt().coerceIn(1, height - 2)
                    val roiBottom = (midPoint.y + eyesDist * 1.4f).toInt().coerceIn(roiTop + 2, height - 1)

                    val faceBlur = computeLaplacianVariance(
                        gray = grayPixels,
                        width = width,
                        height = height,
                        startX = roiLeft,
                        startY = roiTop,
                        endX = roiRight,
                        endY = roiBottom
                    )

                    if (faceBlur < minBlur) {
                        minBlur = faceBlur
                    }

                    // HOLD hanya bila ada wajah cukup besar yang wilayahnya sangat buram:
                    // 1) Di bawah lantai absolut (CULL_THRESHOLD_FACE_BLUR_ABSOLUTE_FLOOR), ATAU
                    // 2) Skor wajah jauh di bawah skor blur global (< 40% global blur)
                    val isBelowFloor = faceBlur < CULL_THRESHOLD_FACE_BLUR_ABSOLUTE_FLOOR
                    val isBelowRelative = (faceBlur < globalBlurScore * CULL_THRESHOLD_FACE_BLUR_GLOBAL_RATIO) &&
                        (faceBlur < CULL_THRESHOLD_BLUR_GLOBAL)

                    if (isBelowFloor || isBelowRelative) {
                        hasSignificantBlurredFace = true
                        blurredReason = String.format(
                            Locale.US,
                            "Wajah utama buram (skor wajah %.1f vs global %.1f, rasio wajah %.0f%%)",
                            faceBlur,
                            globalBlurScore,
                            ratio * 100f
                        )
                    }
                }
            }

            FaceAnalysisResult(
                faceCount = faceCount,
                maxFaceRatio = maxRatio,
                minFaceBlurScore = if (minBlur == Double.MAX_VALUE) 0.0 else minBlur,
                hasSignificantBlurredFace = hasSignificantBlurredFace,
                blurredFaceReason = blurredReason
            )
        } catch (e: Exception) {
            FoundationLog.error("[CULLING] Eksepsi saat mendeteksi wajah", e)
            FaceAnalysisResult(
                faceCount = 0,
                maxFaceRatio = 0.0f,
                minFaceBlurScore = 0.0,
                hasSignificantBlurredFace = false,
                blurredFaceReason = null
            )
        }
    }

    /**
     * Tidur dalam irisan 100 ms agar segera merespons sinyal kick atau shutdown.
     */
    private fun sleepSlices(totalMs: Long) {
        val sliceMs = 100L
        var elapsed = 0L
        while (running && !kicked && elapsed < totalMs) {
            try {
                Thread.sleep(sliceMs)
            } catch (_: InterruptedException) {
                break
            }
            elapsed += sliceMs
        }
        kicked = false
    }
}
