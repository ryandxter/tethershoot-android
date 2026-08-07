package com.reflection.transferin.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of permission request
 */
sealed class PermissionResult {
    data object Granted : PermissionResult()
    data object Denied : PermissionResult()
    data object AlreadyHasPermission : PermissionResult()
}

/**
 * Manages USB permission lifecycle dengan explicit callback ke unlock flow.
 *
 * Flow:
 * 1. startMonitoring() - register receiver
 * 2. requestPermission() - minta izin
 * 3. onPermissionResult() - callback dengan hasil
 * 4. stopMonitoring() - cleanup
 */
class UsbPermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbPermissionManager"
        const val ACTION_USB_PERMISSION = "com.reflection.transferin.USB_PERMISSION"
    }

    private val appContext = context.applicationContext
    private val usbManager: UsbManager by lazy {
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val _permissionState = MutableStateFlow<PermissionResult?>(null)
    val permissionState: StateFlow<PermissionResult?> = _permissionState.asStateFlow()

    private var isRegistered = false
    private var pendingDeviceToOpen: UsbDevice? = null

    // Callback yang dipanggil saat permission granted + device siap di-unlock
    var onPermissionGrantedWithDeviceReady: ((device: UsbDevice) -> Unit)? = null

    // Callback yang dipanggil saat device dicabut
    var onDeviceDetached: ((device: UsbDevice) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action.orEmpty()) {
                ACTION_USB_PERMISSION -> {
                    val device = intent?.let { extractDevice(it) }
                    val granted = intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ?: false

                    Log.d(TAG, "Permission result: granted=$granted, device=${device?.deviceName}")

                    if (granted && device != null) {
                        _permissionState.value = PermissionResult.Granted

                        // CRITICAL: Langsung unlock kamera setelah permission granted
                        onPermissionGrantedWithDeviceReady?.invoke(device)
                    } else {
                        _permissionState.value = PermissionResult.Denied
                        Log.w(TAG, "Permission denied for device=${device?.deviceName}")
                    }

                    pendingDeviceToOpen = null
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent?.let { extractDevice(it) }
                    Log.d(TAG, "USB attached: ${device?.deviceName}")
                    // Reset state jika ada device lama dengan nama sama yang baru dicolok
                    if (device != null && pendingDeviceToOpen?.deviceName == device.deviceName) {
                        pendingDeviceToOpen = null
                        _permissionState.value = null
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent?.let { extractDevice(it) }
                    Log.d(TAG, "USB detached: ${device?.deviceName}")
                    if (device != null) {
                        onDeviceDetached?.invoke(device)
                    }
                    // Reset state
                    if (pendingDeviceToOpen?.deviceName == device?.deviceName) {
                        pendingDeviceToOpen = null
                        _permissionState.value = null
                    }
                }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
        Log.d(TAG, "Monitoring started")
    }

    fun stopMonitoring() {
        if (!isRegistered) return

        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        isRegistered = false
        pendingDeviceToOpen = null
        _permissionState.value = null
        Log.d(TAG, "Monitoring stopped")
    }

    /**
     * Request permission untuk kamera dan langsung trigger unlock setelah granted.
     */
    fun requestPermissionWithAutoUnlock(device: UsbDevice) {
        Log.d(TAG, ">>> requestPermissionWithAutoUnlock: ${device.deviceName}")

        // Check if already has permission
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Already has permission, triggering unlock directly")
            _permissionState.value = PermissionResult.AlreadyHasPermission
            onPermissionGrantedWithDeviceReady?.invoke(device)
            return
        }

        pendingDeviceToOpen = device

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(appContext.packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            device.deviceId,
            intent,
            flags ?: 0
        )

        usbManager.requestPermission(device, permissionIntent)
        Log.d(TAG, "Permission request sent for ${device.deviceName}")
    }

    /**
     * Check apakah device sudah punya permission
     */
    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /**
     * Get semua camera-like devices yang sudah ada permission
     */
    fun getPermittedCameraDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            usbManager.hasPermission(device) && isCameraLike(device)
        }
    }

    /**
     * Get semua camera-like devices (dengan atau tanpa permission)
     */
    fun getAllCameraDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { isCameraLike(it) }
    }

    private fun isCameraLike(device: UsbDevice): Boolean {
        // USB_CLASS_STILL_IMAGE = 6
        if (device.deviceClass == 6) return true

        // Fallback: check keywords in name
        val combined = "${device.manufacturerName ?: ""} ${device.productName ?: ""}".lowercase()
        val cameraKeywords = listOf("camera", "canon", "nikon", "sony", "eos", "eos r", "mirrorless")
        return cameraKeywords.any { combined.contains(it) }
    }

    private fun extractDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
    }
}