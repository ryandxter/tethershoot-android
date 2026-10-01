package com.rvmeur.transferin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.core.UsbHardwareSession
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UsbTetherService : Service() {

    companion object {
        private const val TAG = "UsbTetherService"
        const val ACTION_START_USB_TETHER = "com.rvmeur.transferin.action.START_USB_TETHER"
        const val ACTION_STOP_USB_TETHER = "com.rvmeur.transferin.action.STOP_USB_TETHER"
        const val WAKE_LOCK_TAG = "TransferIn:UsbTetherWakeLock"

        private const val CHANNEL_ID = "usb_tether_channel"
        private const val CHANNEL_NAME = "USB Tether Service"
        private const val NOTIFICATION_ID = 1001

        private const val TEARDOWN_WATCHDOG_TIMEOUT_MS = 3500L
        private const val TEARDOWN_LATCH_AWAIT_MS = 3800L

        @Suppress("SpellCheckingInspection")
        private val CAMERA_KEYWORDS = listOf(
            "camera",
            "canon",
            "nikon",
            "sony",
            "fujifilm",
            "lumix",
            "panasonic",
            "olympus",
            "leica",
            "gopro",
            "dji",
            "eos"
        )
    }

    private lateinit var usbHelper: UsbHelper
    private val serviceJob: CompletableJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var openSessionJob: kotlinx.coroutines.Job? = null
    var activeHardwareSession: UsbHardwareSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        usbHelper = UsbHelper(applicationContext)
        createNotificationChannel()

        // Android 14/15 strict FGS compliance: immediate startForeground within onCreate()
        try {
            val notification = buildNotification("USB camera service is starting")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    serviceTypes
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }
            acquireWakeLock()
            Log.d(TAG, "UsbTetherService created and foreground started with WakeLock")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enter foreground mode in onCreate", t)
            releaseWakeLock()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_USB_TETHER) {
            Log.d(TAG, "Stopping UsbTetherService on ACTION_STOP_USB_TETHER")
            openSessionJob?.cancel()
            releaseWakeLock()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START_USB_TETHER) {
            Log.w(TAG, "Ignoring unsupported action=${intent?.action}")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val device = extractUsbDevice(intent)
        if (device == null) {
            Log.w(TAG, "No USB device found in start intent")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        Log.d(
            TAG,
            "UsbTetherService start device=${device.deviceName} vendorId=${device.vendorId} productId=${device.productId}"
        )

        if (!isLikelyCamera(device)) {
            Log.d(TAG, "Device is not camera-like, stopping service: ${device.deviceName}")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        acquireWakeLock()

        openSessionJob?.cancel()
        openSessionJob = serviceScope.launch {
            updateNotification("Opening camera session...")

            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    usbHelper.refreshUsbState()
                    usbHelper.openCameraSession(device.deviceName)
                }.onFailure { throwable ->
                    Log.e(TAG, "Failed to open camera session", throwable)
                }.getOrDefault(false)
            }

            if (!opened) {
                Log.w(TAG, "Camera session open failed for ${device.deviceName}")
                updateNotification("Failed to open camera session")
                releaseWakeLock()
                stopSelfResult(startId)
                return@launch
            }

            Log.d(TAG, "Camera session opened from service for ${device.deviceName}")
            updateNotification("USB camera connected")
        }

        return START_NOT_STICKY
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onDestroy() {
        openSessionJob?.cancel()

        val latch = CountDownLatch(1)

        // Dispatch clean teardown in unconfined IO + NonCancellable context with 3.5s watchdog timeout
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                withTimeoutOrNull(TEARDOWN_WATCHDOG_TIMEOUT_MS) {
                    runCatching {
                        activeHardwareSession?.close()
                    }.onFailure { Log.w(TAG, "Failed closing activeHardwareSession", it) }

                    runCatching {
                        usbHelper.closeCameraSessionSuspend()
                    }.onFailure { Log.w(TAG, "Failed closing camera session on destroy", it) }
                }
            } finally {
                latch.countDown()
            }
        }

        try {
            latch.await(TEARDOWN_LATCH_AWAIT_MS, TimeUnit.MILLISECONDS)
        } catch (ignored: InterruptedException) {
            Log.w(TAG, "Teardown latch interrupted")
        }

        serviceJob.cancel()
        releaseWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        Log.d(TAG, "UsbTetherService destroyed cleanly")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Synchronized
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(PowerManager::class.java)
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
                Log.d(TAG, "Acquired partial WakeLock: $WAKE_LOCK_TAG")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to acquire WakeLock", t)
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released partial WakeLock: $WAKE_LOCK_TAG")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release WakeLock", t)
        } finally {
            wakeLock = null
        }
    }

    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun isLikelyCamera(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
            device.deviceClass == UsbConstants.USB_CLASS_VIDEO
        ) {
            return true
        }

        repeat(device.interfaceCount) { index ->
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_VIDEO
            ) {
                return true
            }
        }

        val combinedName = buildString {
            append(device.manufacturerName.orEmpty())
            append(" ")
            append(device.productName.orEmpty())
        }.lowercase()

        return CAMERA_KEYWORDS.any { keyword -> combinedName.contains(keyword) }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager?.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground service for USB camera tethering"
            setShowBadge(false)
        }

        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TransferIn")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.PermissionChecker.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            androidx.core.content.PermissionChecker.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission")
            return
        }
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }
}