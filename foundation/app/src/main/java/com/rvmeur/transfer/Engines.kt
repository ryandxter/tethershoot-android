package com.rvmeur.transfer

import android.content.Context
import android.content.Intent
import android.os.Build

object Engines {
    lateinit var db: TransferInDb
        private set
    lateinit var driveAuth: DriveAuth
        private set
    lateinit var driveFolders: DriveFolders
        private set
    lateinit var driveUploader: DriveUploader
        private set
    lateinit var uploadQueue: UploadQueue
        private set
    lateinit var cullingGate: CullingGate
        private set
    lateinit var ptpSession: PtpSession
        private set
    lateinit var orchestrator: SessionOrchestrator
        private set

    @Volatile
    var serviceRunning: Boolean = false

    @Volatile
    var initialized: Boolean = false
        private set

    @Volatile
    private var crashHandlerInstalled: Boolean = false

    @Synchronized
    fun init(context: Context) {
        if (!crashHandlerInstalled) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    FoundationLog.error("[CRASH] Uncaught di thread ${thread.name}", throwable)
                    FoundationLog.log(throwable.stackTraceToString())
                } catch (_: Throwable) {
                }
                previous?.uncaughtException(thread, throwable)
            }
            crashHandlerInstalled = true
        }
        if (initialized) return
        val ctx = context.applicationContext
        db = TransferInDb.getInstance(ctx)
        driveAuth = DriveAuth.getInstance(ctx)
        driveFolders = DriveFolders(ctx, driveAuth)
        driveUploader = DriveUploader(ctx, driveAuth, db)
        uploadQueue = UploadQueue(ctx, driveAuth, driveUploader, db)
        uploadQueue.start()
        cullingGate = CullingGate(ctx, db, uploadQueue)
        cullingGate.start()
        ptpSession = PtpSession(ctx)
        orchestrator = SessionOrchestrator(ctx, ptpSession, db, uploadQueue)
        orchestrator.cullingGate = cullingGate
        cullingGate.orchestratorRef = { orchestrator }
        initialized = true
        FoundationLog.log("[LAYANAN] Mesin proses diinisialisasi")
    }

    fun shouldServiceRun(): Boolean {
        if (!initialized) return false
        val sessionActive = try {
            orchestrator.state != SessionOrchestrator.State.IDLE &&
                orchestrator.state != SessionOrchestrator.State.CLOSED
        } catch (_: Throwable) {
            false
        }
        if (sessionActive) return true

        return try {
            val counts = db.countsByStatus()
            val queued = counts[ItemStatus.QUEUED] ?: 0
            val uploading = counts[ItemStatus.UPLOADING] ?: 0
            val verifying = counts[ItemStatus.VERIFYING] ?: 0
            val cullingPending = counts[ItemStatus.CULLING_PENDING] ?: 0
            (queued + uploading + verifying + cullingPending) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun syncService(context: Context) {
        val shouldRun = shouldServiceRun()
        val intent = Intent(context, TransferInService::class.java)
        if (shouldRun && !serviceRunning) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                FoundationLog.log("[LAYANAN] Layanan latar depan dimulai")
            } catch (e: Throwable) {
                FoundationLog.log("[LAYANAN] Gagal memulai layanan latar depan: ${e.message}")
            }
        } else if (!shouldRun && serviceRunning) {
            try {
                context.stopService(intent)
                FoundationLog.log("[LAYANAN] Layanan latar depan dihentikan")
            } catch (e: Throwable) {
                FoundationLog.log("[LAYANAN] Gagal menghentikan layanan latar depan: ${e.message}")
            }
        }
    }
}
