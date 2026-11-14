package com.rvmeur.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Pusat notifikasi tunggal untuk TransferIn Foundation.
 * Menangani notifikasi lokal penyelesaian unggahan, sesi tuntas, dan ketersediaan pembaruan OTA.
 *
 * Kelak layanan FCM akan memanggil object ini juga untuk menyajikan notifikasi cloud/remote.
 */
object InfoNotifier {

    const val CHANNEL_ID = "transferin-info"
    private const val CHANNEL_NAME = "info tether"

    const val ID_UPLOADS_DONE = 4301
    const val ID_SESSION_DONE = 4302
    const val ID_UPDATE = 4303

    @Volatile
    private var channelCreated = false

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
                channelCreated = true
            } catch (t: Throwable) {
                FoundationLog.error("[NOTIF] Gagal membuat channel notifikasi", t)
            }
        } else {
            channelCreated = true
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                FoundationLog.log("[NOTIF] izin notifikasi belum diberikan — lewati")
                return false
            }
        }
        return true
    }

    private fun sendNotification(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        iconRes: Int
    ) {
        try {
            if (!hasNotificationPermission(context)) return

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm == null) {
                FoundationLog.error("[NOTIF] NotificationManager tidak tersedia")
                return
            }

            ensureChannel(context, nm)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            val notification = builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(iconRes)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(notificationId, notification)
        } catch (t: Throwable) {
            FoundationLog.error("[NOTIF] Gagal menampilkan notifikasi ($notificationId)", t)
        }
    }

    fun notifyUploadsDone(context: Context) {
        sendNotification(
            context = context,
            notificationId = ID_UPLOADS_DONE,
            title = "@rvmeur tether",
            text = "unggahan selesai — semua foto sudah di drive.",
            iconRes = android.R.drawable.stat_sys_upload_done
        )
    }

    fun notifySessionDone(context: Context) {
        sendNotification(
            context = context,
            notificationId = ID_SESSION_DONE,
            title = "@rvmeur tether",
            text = "sesi tuntas — antrean unggahan sudah kosong.",
            iconRes = android.R.drawable.stat_notify_sync
        )
    }

    fun notifyUpdateAvailable(context: Context, versionName: String) {
        sendNotification(
            context = context,
            notificationId = ID_UPDATE,
            title = "@rvmeur tether",
            text = "pembaruan tersedia: v$versionName — buka aplikasi untuk memasang.",
            iconRes = android.R.drawable.stat_sys_download_done
        )
    }
}
