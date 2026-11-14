package com.rvmeur.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder

class TransferInService : Service() {

    companion object {
        private const val CHANNEL_ID = "transferin-aktif"
        private const val NOTIFICATION_ID = 4211
    }

    private var detachReceiver: BroadcastReceiver? = null

    @Volatile
    private var isPolling = false
    private var pollingThread: Thread? = null

    @Volatile
    private var lastNotificationText: String? = null

    @Volatile
    private var latestStartId: Int = 0

    private var sawWork = false
    private var sessionWasActive = false

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TransferIn aktif",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    if (Engines.initialized &&
                        Engines.orchestrator.state != SessionOrchestrator.State.IDLE &&
                        Engines.orchestrator.state != SessionOrchestrator.State.CLOSED
                    ) {
                        FoundationLog.log("[LAYANAN] Broadcast USB detached diterima — kabel dicabut")
                        Engines.orchestrator.stopForCablePull()
                    }
                }
            }
        }
        detachReceiver = receiver
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        Engines.serviceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        Engines.init(applicationContext)

        val initialText = resolveNotificationText()
        lastNotificationText = initialText
        val notification = buildNotification(initialText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isPolling) {
            isPolling = true
            pollingThread = Thread({
                while (isPolling) {
                    try {
                        Thread.sleep(2000L)
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (!isPolling) break

                    val shouldRun = Engines.shouldServiceRun()
                    if (shouldRun) {
                        sawWork = true
                        val isSessionActive = try {
                            Engines.initialized &&
                                Engines.orchestrator.state != SessionOrchestrator.State.IDLE &&
                                Engines.orchestrator.state != SessionOrchestrator.State.CLOSED
                        } catch (_: Throwable) {
                            false
                        }
                        if (isSessionActive) {
                            sessionWasActive = true
                        }

                        val currentText = resolveNotificationText()
                        if (currentText != lastNotificationText) {
                            lastNotificationText = currentText
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(NOTIFICATION_ID, buildNotification(currentText))
                        }
                    } else {
                        if (sawWork) {
                            try {
                                if (sessionWasActive) {
                                    InfoNotifier.notifySessionDone(applicationContext)
                                } else {
                                    InfoNotifier.notifyUploadsDone(applicationContext)
                                }
                            } catch (t: Throwable) {
                                FoundationLog.error("[LAYANAN] Gagal memicu notifikasi penyelesaian", t)
                            }
                            sawWork = false
                            sessionWasActive = false
                        }

                        if (stopSelfResult(latestStartId)) {
                            FoundationLog.log("[LAYANAN] Semua pekerjaan tuntas — layanan berhenti")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                            }
                            isPolling = false
                            break
                        } else {
                            // start yang lebih baru tiba — layanan tetap hidup; lanjutkan polling
                        }
                    }
                }
            }, "TransferInService-Polling").apply {
                isDaemon = true
                start()
            }
        }

        return START_STICKY
    }

    fun buildNotification(text: String = resolveNotificationText()): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("TransferIn")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun resolveNotificationText(): String {
        val sessionActive = try {
            Engines.initialized &&
                Engines.orchestrator.state != SessionOrchestrator.State.IDLE &&
                Engines.orchestrator.state != SessionOrchestrator.State.CLOSED
        } catch (_: Throwable) {
            false
        }

        val counts = try {
            if (Engines.initialized) Engines.db.countsByStatus() else emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }

        val done = counts[ItemStatus.DONE] ?: 0
        val queued = counts[ItemStatus.QUEUED] ?: 0
        val inProgress = (counts[ItemStatus.UPLOADING] ?: 0) + (counts[ItemStatus.VERIFYING] ?: 0)

        return if (sessionActive) {
            "Sesi kamera berjalan — selesai $done, antre $queued"
        } else {
            "Mengunggah — antre $queued, proses $inProgress"
        }
    }

    override fun onDestroy() {
        isPolling = false
        pollingThread?.interrupt()
        pollingThread = null

        detachReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Throwable) {
            }
            detachReceiver = null
        }

        Engines.serviceRunning = false
        if (Engines.shouldServiceRun()) {
            try {
                startForegroundService(Intent(this, TransferInService::class.java))
                FoundationLog.log("[LAYANAN] Layanan dihidupkan lagi dari onDestroy — pekerjaan belum tuntas")
            } catch (t: Throwable) {
                FoundationLog.error("[LAYANAN] Gagal menghidupkan lagi layanan dari onDestroy", t)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
