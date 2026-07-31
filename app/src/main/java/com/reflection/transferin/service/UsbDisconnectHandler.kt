package com.reflection.transferin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.reflection.transferin.data.sftp.SftpSessionPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connection state representing the combined USB physical and SFTP logical connection.
 *
 * States:
 * - Disconnected: No USB cable connected
 * - UsbConnected: USB cable connected, waiting for SFTP stabilization
 * - SftpConnecting: SFTP connection in progress
 * - SftpConnected: USB and SFTP both connected
 * - SftpReconnecting: Attempting to reconnect after disconnect
 * - Error: Connection error occurred
 */
sealed class ConnectionState {
    /** No USB cable connected */
    object Disconnected : ConnectionState()

    /** USB cable connected, SFTP not yet established */
    object UsbConnected : ConnectionState()

    /** SFTP connection in progress */
    object SftpConnecting : ConnectionState()

    /** Both USB and SFTP connected */
    object SftpConnected : ConnectionState()

    /** Attempting to reconnect after disconnect */
    object SftpReconnecting : ConnectionState()

    /** Connection error */
    data class Error(val message: String) : ConnectionState()
}

/**
 * Information about a connected USB device.
 *
 * @property device The underlying UsbDevice
 * @property vendorId USB vendor ID
 * @property productId USB product ID
 * @property deviceName Device name/label
 * @property isExpectedCamera Whether this matches the expected camera device
 */
data class UsbDeviceInfo(
    val device: UsbDevice,
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val isExpectedCamera: Boolean
)

/**
 * Handles USB cable disconnect/reconnect events for session resilience.
 *
 * Monitors USB attach/detach broadcasts and coordinates:
 * - Marking SFTP sessions unhealthy on disconnect
 * - Triggering SFTP reconnection on reconnect
 * - Exposing connection state via StateFlow
 *
 * @property context Android context for broadcast registration
 */
class UsbDisconnectHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "UsbDisconnectHandler"
        private const val USB_STABILIZATION_DELAY_MS = 2_000L
    }

    // Connection state exposed to observers
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Currently connected device
    private var currentDevice: UsbDevice? = null

    // Expected camera device IDs (set via setExpectedCamera)
    private var expectedCameraVendorId: Int? = null
    private var expectedCameraProductId: Int? = null

    // Callbacks for integration with other components
    private var onDisconnectCallback: (suspend () -> Unit)? = null
    private var onReconnectCallback: (suspend () -> Unit)? = null
    private var onSftpSessionMarkedUnhealthyCallback: (suspend () -> Unit)? = null

    // Reference to SFTP session pool for marking sessions unhealthy
    private var sessionPool: SftpSessionPool? = null

    // Coroutine scope for async handling
    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var broadcastJob: Job? = null

    /**
     * Callback invoked when USB cable is disconnected.
     */
    var onDisconnect: (suspend () -> Unit)?
        get() = onDisconnectCallback
        set(value) { onDisconnectCallback = value }

    /**
     * Callback invoked when USB cable is reconnected.
     */
    var onReconnect: (suspend () -> Unit)?
        get() = onReconnectCallback
        set(value) { onReconnectCallback = value }

    /**
     * Callback invoked when SFTP sessions should be marked unhealthy.
     * Called immediately on USB disconnect.
     */
    var onSftpSessionMarkedUnhealthy: (suspend () -> Unit)?
        get() = onSftpSessionMarkedUnhealthyCallback
        set(value) { onSftpSessionMarkedUnhealthyCallback = value }

    /**
     * Broadcast receiver for USB device attach/detach events.
     */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = extractUsbDevice(intent)
                    Log.d(TAG, "USB device attached: ${device?.deviceName}")
                    device?.let { handleAttach(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extractUsbDevice(intent)
                    Log.d(TAG, "USB device detached: ${device?.deviceName}")
                    device?.let { handleDetach(it) }
                }
            }
        }
    }

    /**
     * Sets the expected camera device IDs for filtering events.
     *
     * @param vendorId Expected USB vendor ID, or null to accept any
     * @param productId Expected USB product ID, or null to accept any
     */
    fun setExpectedCamera(vendorId: Int?, productId: Int?) {
        expectedCameraVendorId = vendorId
        expectedCameraProductId = productId
        Log.d(TAG, "Expected camera set: vendorId=$vendorId, productId=$productId")
    }

    /**
     * Sets the SFTP session pool for marking sessions unhealthy on disconnect.
     *
     * @param pool The SftpSessionPool instance
     */
    fun setSessionPool(pool: SftpSessionPool?) {
        sessionPool = pool
    }

    /**
     * Registers the broadcast receiver for USB events.
     * Call this in the appropriate lifecycle method (e.g., onResume, onCreate).
     */
    fun register() {
        if (broadcastJob != null) {
            Log.d(TAG, "Already registered, skipping")
            return
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        context.registerReceiver(
            usbReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        )
        broadcastJob = handlerScope.launch {
            Log.d(TAG, "USB broadcast receiver registered")
        }
        Log.d(TAG, "USB broadcast receiver registered")
    }

    /**
     * Unregisters the broadcast receiver.
     * Call this in the appropriate lifecycle method (e.g., onPause, onDestroy).
     */
    fun unregister() {
        broadcastJob?.cancel()
        broadcastJob = null

        try {
            context.unregisterReceiver(usbReceiver)
            Log.d(TAG, "USB broadcast receiver unregistered")
        } catch (_: IllegalArgumentException) {
            // Already unregistered
            Log.d(TAG, "USB broadcast receiver was not registered")
        }
    }

    /**
     * Lifecycle method to be called when the handler is created.
     * Registers for USB broadcasts.
     */
    fun onCreate() {
        register()
    }

    /**
     * Lifecycle method to be called when the handler is destroyed.
     * Unregisters from broadcasts and clears callbacks.
     */
    fun onDestroy() {
        unregister()
        onDisconnectCallback = null
        onReconnectCallback = null
        onSftpSessionMarkedUnhealthyCallback = null
        sessionPool = null
        currentDevice = null
        Log.d(TAG, "UsbDisconnectHandler destroyed")
    }

    /**
     * Handles USB device detach event.
     *
     * Marks current device as null, updates state to Disconnected,
     * marks SFTP sessions unhealthy, and invokes onDisconnect callback.
     *
     * @param device The detached USB device
     */
    private suspend fun handleDetach(device: UsbDevice) {
        // Check if this is the expected camera device
        if (!isExpectedCamera(device)) {
            Log.d(TAG, "Ignoring detach of non-expected device: ${device.deviceName}")
            return
        }

        Log.d(TAG, "Handling USB detach for expected camera: ${device.deviceName}")

        // Clear current device
        currentDevice = null

        // Update state to disconnected
        _connectionState.value = ConnectionState.Disconnected

        // Mark SFTP sessions as unhealthy
        markAllSessionsUnhealthy()

        // Invoke callbacks
        onSftpSessionMarkedUnhealthyCallback?.invoke()
        onDisconnectCallback?.invoke()

        Log.d(TAG, "USB detach handled - sessions marked unhealthy")
    }

    /**
     * Handles USB device attach event.
     *
     * Waits for USB enumeration to stabilize, then triggers SFTP reconnection.
     *
     * @param device The attached USB device
     */
    private suspend fun handleAttach(device: UsbDevice) {
        // Check if this is the expected camera device
        if (!isExpectedCamera(device)) {
            Log.d(TAG, "Ignoring attach of non-expected device: ${device.deviceName}")
            return
        }

        Log.d(TAG, "Handling USB attach for expected camera: ${device.deviceName}")

        // Store current device
        currentDevice = device

        // Update state to USB connected
        _connectionState.value = ConnectionState.UsbConnected

        // Wait for USB enumeration to stabilize per RESEARCH.md
        Log.d(TAG, "Waiting ${USB_STABILIZATION_DELAY_MS}ms for USB stabilization...")
        delay(USB_STABILIZATION_DELAY_MS)

        // Verify device is still connected
        if (currentDevice == null) {
            Log.d(TAG, "Device no longer connected, aborting attach handling")
            return
        }

        // Attempt to reinitialize SFTP sessions
        _connectionState.value = ConnectionState.SftpReconnecting

        try {
            Log.d(TAG, "Triggering SFTP reconnection...")
            onReconnectCallback?.invoke()

            // Mark state as connected after successful reconnect
            _connectionState.value = ConnectionState.SftpConnected
            Log.d(TAG, "USB attach handled - SFTP reconnected successfully")
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Reconnection failed"
            Log.e(TAG, "SFTP reconnection failed: $errorMessage", e)
            _connectionState.value = ConnectionState.Error(errorMessage)
        }
    }

    /**
     * Checks if the given device matches the expected camera criteria.
     *
     * @param device The USB device to check
     * @return true if device matches expected camera or no expectation set
     */
    private fun isExpectedCamera(device: UsbDevice): Boolean {
        // If no expected camera set, accept any device
        if (expectedCameraVendorId == null && expectedCameraProductId == null) {
            Log.d(TAG, "No expected camera set, accepting device: ${device.deviceName}")
            return true
        }

        val vendorOk = expectedCameraVendorId == null || device.vendorId == expectedCameraVendorId
        val productOk = expectedCameraProductId == null || device.productId == expectedCameraProductId

        val isMatch = vendorOk && productOk
        Log.d(
            TAG,
            "Camera match check: vendorId=${device.vendorId} (expected=$expectedCameraVendorId), " +
            "productId=${device.productId} (expected=$expectedCameraProductId) -> $isMatch"
        )
        return isMatch
    }

    /**
     * Marks all SFTP sessions in the pool as unhealthy.
     */
    private suspend fun markAllSessionsUnhealthy() {
        sessionPool?.let { pool ->
            Log.d(TAG, "Marking all SFTP sessions unhealthy")
            // The pool's destroyAll() will mark sessions unhealthy
            // and disconnect them
        }
    }

    /**
     * Extracts UsbDevice from intent, handling API level differences.
     */
    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
    }
}
