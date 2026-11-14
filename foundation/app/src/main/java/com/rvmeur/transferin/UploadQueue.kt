package com.rvmeur.transferin

import android.content.Context
import java.io.File

/**
 * Penguras antrean unggah 3 pekerja paralel ("upload-drainer") untuk Google Drive.
 *
 * Menerapkan:
 * - Siklus hidup start/stop (onCreate/onDestroy di MainActivity).
 * - Pola tidur irisan + flag @Volatile + kick() berbasis penghitung generasi tanpa wait/notify.
 * - Penanganan autentikasi: tidur 30 detik bila token belum tersedia, item tetap QUEUED.
 * - Pembersihan berkas lokal DONE yang sesinya sudah CLOSED (BRS Q8) dengan penanda log [BERSIH].
 * - Penutupan sesi via onSessionClosed(sessionId): tandai CLOSED, sapu item DONE yang tersisa, dan kick.
 */
class UploadQueue(
    private val context: Context,
    private val driveAuth: DriveAuth,
    private val driveUploader: DriveUploader,
    private val db: TransferInDb
) {

    companion object {
        const val WORKER_COUNT = 3
    }

    @Volatile
    private var running: Boolean = false

    private val kickGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    private val workerThreads = mutableListOf<Thread>()
    private val claimLock = Any()

    init {
        // Tautkan pembatalan uploader saat antrean dimatikan
        driveUploader.isCancelled = { !running }
    }

    /**
     * Memulai thread pekerja penguras antrean unggah.
     */
    fun start() {
        if (running) return
        running = true

        // Pulihkan item yang terdampar di UPLOADING/VERIFYING oleh proses sebelumnya
        try {
            val recovered = db.requeueStaleInFlight()
            if (recovered > 0) {
                FoundationLog.log("[UNGGAH] Pemulihan: $recovered item terdampar dikembalikan ke QUEUED")
            }
        } catch (t: Throwable) {
            FoundationLog.error("[UNGGAH] Pemulihan item terdampar gagal", t)
        }

        for (i in 1..WORKER_COUNT) {
            val workerName = "upload-worker-$i"
            val thread = Thread({
                FoundationLog.log("[UNGGAH] Thread penguras $workerName dimulai")
                runDrainerLoop()
                FoundationLog.log("[UNGGAH] Thread penguras $workerName berhenti")
            }, workerName).apply {
                isDaemon = true
                start()
            }
            workerThreads.add(thread)
        }
    }

    /**
     * Menghentikan thread pekerja penguras antrean secara aman.
     */
    fun stop() {
        running = false
        kick()
        for (thread in workerThreads) {
            try {
                thread.join(1000)
            } catch (_: Exception) {
            }
        }
        workerThreads.clear()
    }

    /**
     * Membangunkan thread penguras dari tidur irisan sesegera mungkin.
     */
    fun kick() {
        kickGeneration.incrementAndGet()
    }

    /**
     * Menandai sesi CLOSED di database dan menyapu berkas lokal item yang sudah DONE.
     */
    fun onSessionClosed(sessionId: String) {
        FoundationLog.log("[UNGGAH] onSessionClosed untuk sesi $sessionId: menutup sesi di DB dan menyapu berkas DONE...")
        db.closeSession(sessionId)
        driveUploader.clearSessionIndex(sessionId)

        // Sapu seluruh item yang sudah berstatus DONE pada sesi ini
        val doneItems = db.getItemsBySessionAndStatus(sessionId, ItemStatus.DONE)
        for (item in doneItems) {
            cleanLocalFile(item.localPath)
        }

        kick()
    }

    private fun runDrainerLoop() {
        while (running) {
            // 1. Periksa ketersediaan autentikasi Google Drive
            val token = driveAuth.getAccessToken()
            if (token == null) {
                FoundationLog.log("[UNGGAH] Menunggu login Google Drive (token belum tersedia)...")
                sleepSlices(30_000L)
                continue
            }

            var item: TransferItem? = null
            try {
                // 2. Ambil item antrean berikutnya (FIFO berstatus QUEUED) secara atomik dan tandai UPLOADING
                item = synchronized(claimLock) {
                    val next = db.nextQueuedItem()
                    if (next != null) {
                        val claimed = db.tryTransitionStatus(next.id, ItemStatus.UPLOADING, listOf(ItemStatus.QUEUED))
                        if (claimed) next else null
                    } else {
                        null
                    }
                }
                if (item == null) {
                    // Antrean sedang kosong, tidur irisan 2 detik atau sampai di-kick
                    sleepSlices(2_000L)
                    continue
                }

                FoundationLog.log("[UNGGAH] Memulai unggah item: ${item.filename} (${item.sizeBytes} B) oleh ${Thread.currentThread().name}")

                // 4. Proses unggah melalui DriveUploader
                val uploadResult = driveUploader.upload(item)

                // 5. Tangani hasil unggah
                when (uploadResult) {
                    DriveUploader.UploadResult.SUCCESS -> {
                        // Item sudah dinyatakan DONE dan terverifikasi di dalam uploader
                        // Jika sesinya sudah CLOSED, hapus berkas lokal
                        if (db.isSessionClosed(item.sessionId)) {
                            cleanLocalFile(item.localPath)
                        }
                    }

                    DriveUploader.UploadResult.AUTH_ERROR -> {
                        // Kembalikan ke QUEUED agar dapat dicoba lagi setelah login
                        val transitioned = db.tryTransitionStatus(
                            item.id,
                            ItemStatus.QUEUED,
                            listOf(ItemStatus.UPLOADING, ItemStatus.VERIFYING)
                        )
                        if (transitioned) {
                            FoundationLog.log("[UNGGAH] Autentikasi gagal saat mengunggah ${item.filename}, item dikembalikan ke QUEUED")
                            sleepSlices(30_000L)
                        } else {
                            FoundationLog.log("[UNGGAH] Item ${item.filename} sudah berpindah status (dibuang/selesai) — pengembalian ke antrean dilewati")
                        }
                    }

                    DriveUploader.UploadResult.RETRYABLE_ERROR -> {
                        // WAJIB kembali ke QUEUED: tanpa ini item terdampar permanen di
                        // UPLOADING (nextQueuedItem hanya mengambil QUEUED) dan menahan
                        // seluruh antrean di belakangnya — cacat terbukti pada uji
                        // Stage 3 sesi 20261109-181056. Melanjutkan aman lewat sesi
                        // resumable yang tersimpan di baris item.
                        val transitioned = db.tryTransitionStatus(
                            item.id,
                            ItemStatus.QUEUED,
                            listOf(ItemStatus.UPLOADING, ItemStatus.VERIFYING)
                        )
                        if (transitioned) {
                            FoundationLog.log("[UNGGAH] Item ${item.filename} dikembalikan ke QUEUED untuk dicoba lagi")
                            if (running) {
                                sleepSlices(2_000L)
                            }
                        } else {
                            FoundationLog.log("[UNGGAH] Item ${item.filename} sudah berpindah status (dibuang/selesai) — pengembalian ke antrean dilewati")
                        }
                    }

                    DriveUploader.UploadResult.FAILED_FINAL -> {
                        // Status FAILED_FINAL sudah diperbarui oleh uploader
                        FoundationLog.error("[UNGGAH] Item ${item.filename} selesai dengan status FAILED_FINAL")
                    }

                    DriveUploader.UploadResult.CANCELLED -> {
                        // Item dibatalkan karena telah dibuang pengguna:
                        // TIDAK menimpa status DISCARDED di DB, tidak dihitung FAILED, dan tidak diubah menjadi RETRYABLE.
                        FoundationLog.log("[UNGGAH] Item ${item.filename} dibatalkan karena telah dibuang pengguna")
                    }
                }
            } catch (t: Throwable) {
                FoundationLog.error("[UNGGAH] Eksepsi liar pada pekerja ${Thread.currentThread().name}", t)
                if (item != null) {
                    try {
                        db.tryTransitionStatus(
                            item.id,
                            ItemStatus.QUEUED,
                            listOf(ItemStatus.UPLOADING, ItemStatus.VERIFYING)
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    /**
     * Menghapus berkas lokal dengan penanda log [BERSIH] jika berkas masih ada di penyimpanan.
     */
    private fun cleanLocalFile(localPath: String) {
        try {
            val file = File(localPath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    FoundationLog.log("[BERSIH] Berkas lokal dihapus: $localPath")
                } else {
                    FoundationLog.error("[BERSIH] Gagal menghapus berkas lokal: $localPath")
                }
            }
            val previewFile = File(file.parentFile, file.nameWithoutExtension + ".preview.jpg")
            if (previewFile.exists()) {
                val previewDeleted = previewFile.delete()
                if (previewDeleted) {
                    FoundationLog.log("[BERSIH] Berkas lokal dihapus: ${previewFile.absolutePath}")
                } else {
                    FoundationLog.error("[BERSIH] Gagal menghapus berkas lokal: ${previewFile.absolutePath}")
                }
            }
        } catch (t: Throwable) {
            FoundationLog.error("[BERSIH] Eksepsi saat menghapus berkas lokal: $localPath", t)
        }
    }

    /**
     * Tidur dalam irisan 100 ms sehingga responsif terhadap pemanggilan [kick] atau [stop].
     */
    private fun sleepSlices(totalMs: Long) {
        val gen = kickGeneration.get()
        val sliceMs = 100L
        var elapsed = 0L
        while (running && kickGeneration.get() == gen && elapsed < totalMs) {
            try {
                Thread.sleep(sliceMs)
            } catch (_: InterruptedException) {
                break
            }
            elapsed += sliceMs
        }
    }
}
