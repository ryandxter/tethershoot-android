package com.rvmeur.transfer

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Utilitas FCM untuk TransferIn Foundation.
 * Berjalan aman dalam try/catch(Throwable) agar ketiadaan Play Services
 * tidak pernah menjatuhkan aplikasi.
 */
object FcmSupport {

    fun censorToken(token: String?): String {
        if (token == null) return "<null>"
        if (token.length < 13) return "<token pendek>"
        return "${token.take(6)}…${token.takeLast(6)}"
    }

    fun fetchTokenAsync() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        FoundationLog.log("[FCM] token: ${censorToken(task.result)}")
                    } else {
                        FoundationLog.error("[FCM] ambil token gagal", task.exception)
                    }
                } catch (t: Throwable) {
                    FoundationLog.error("[FCM] tidak tersedia: ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            FoundationLog.error("[FCM] tidak tersedia: ${t.message}", t)
        }
    }

    fun requestToken(callback: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        val token = task.result
                        Handler(Looper.getMainLooper()).post {
                            try {
                                callback(token)
                            } catch (_: Throwable) {}
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                callback(null)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {
                    try {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                callback(null)
                            } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {
                        callback(null)
                    }
                }
            }
        } catch (_: Throwable) {
            try {
                Handler(Looper.getMainLooper()).post {
                    try {
                        callback(null)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                callback(null)
            }
        }
    }
}
