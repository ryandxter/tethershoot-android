package com.rvmeur.transfer

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Layanan penerima pesan Firebase Cloud Messaging (FCM).
 * Menangani pembaruan token dan meneruskan pesan masuk ke InfoNotifier.
 */
class TetherMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            FoundationLog.log("[FCM] token baru: ${FcmSupport.censorToken(token)}")
        } catch (t: Throwable) {
            FoundationLog.error("[FCM] gagal mencatat token baru", t)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        try {
            val notif = message.notification
            var title = notif?.title
            var body = notif?.body

            if (title.isNullOrBlank()) {
                title = message.data["title"]
            }
            if (body.isNullOrBlank()) {
                body = message.data["body"]
            }

            if ((title.isNullOrBlank() || body.isNullOrBlank()) && message.data.isNotEmpty()) {
                if (title.isNullOrBlank()) {
                    title = "@rvmeur tether"
                }
                if (body.isNullOrBlank()) {
                    val summary = message.data.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    body = if (summary.length > 120) summary.take(120) else summary
                }
            }

            val finalTitle = title?.takeIf { it.isNotBlank() } ?: "@rvmeur tether"
            val finalBody = body?.takeIf { it.isNotBlank() } ?: ""

            InfoNotifier.showRemote(context = this, title = finalTitle, body = finalBody)
        } catch (t: Throwable) {
            FoundationLog.error("[FCM] gagal memproses pesan masuk", t)
        }
    }
}
