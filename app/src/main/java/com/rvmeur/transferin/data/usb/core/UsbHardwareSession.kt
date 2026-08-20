package com.rvmeur.transferin.data.usb.core

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.util.Log
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Deterministic lifecycle states for a USB hardware session.
 */
sealed class HardwareSessionState {
    object Idle : HardwareSessionState()
    data class Connected(val deviceName: String, val vendorId: Int, val productId: Int) : HardwareSessionState()
    data class Handshake(val stage: String) : HardwareSessionState()
    data class ActiveStreaming(val activeTransfers: Int) : HardwareSessionState()
    data class Disconnecting(val reason: String) : HardwareSessionState()
    object CleanTerminated : HardwareSessionState()
    data class Failed(val error: Throwable) : HardwareSessionState()
}

/**
 * Manages the deterministic lifecycle and resources of an active USB hardware connection.
 * Implements [AutoCloseable] with a strictly sequenced 5-step non-cancellable teardown.
 */
class UsbHardwareSession(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    transportManagerOverride: SafeUsbTransportManager? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "UsbHardwareSession"
        private const val DEFAULT_DRAIN_TIMEOUT_MS = 800L
        private const val DEFAULT_TEARDOWN_TIMEOUT_MS = 3000L
    }

    private val sessionJob: CompletableJob = SupervisorJob()
    val sessionScope = CoroutineScope(sessionJob + Dispatchers.IO)

    val transportManager: SafeUsbTransportManager =
        transportManagerOverride ?: SafeUsbTransportManager(connection)

    private val _claimedInterfaces = CopyOnWriteArrayList<UsbInterface>()
    val claimedInterfaces: List<UsbInterface> get() = _claimedInterfaces

    private val _state = MutableStateFlow<HardwareSessionState>(
        HardwareSessionState.Connected(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId
        )
    )
    val state: StateFlow<HardwareSessionState> = _state.asStateFlow()

    private val isClosedFlag = AtomicBoolean(false)
    val isClosed: Boolean get() = isClosedFlag.get()

    /**
     * Optional vendor-specific teardown hook (e.g. Fuji 0xD214=0 or PTP CloseSession 0x1003).
     */
    var onVendorTeardown: (suspend () -> Unit)? = null

    /**
     * Claims a [UsbInterface] for exclusive transfer operations.
     */
    fun claimInterface(usbInterface: UsbInterface, force: Boolean = true): Boolean {
        if (isClosedFlag.get()) return false
        val claimed = connection.claimInterface(usbInterface, force)
        if (claimed && !_claimedInterfaces.contains(usbInterface)) {
            _claimedInterfaces.add(usbInterface)
            Log.d(TAG, "Claimed USB interface ${usbInterface.id} for ${device.deviceName}")
        }
        return claimed
    }

    /**
     * Releases a previously claimed [UsbInterface].
     */
    fun releaseInterface(usbInterface: UsbInterface): Boolean {
        _claimedInterfaces.remove(usbInterface)
        return runCatching {
            connection.releaseInterface(usbInterface)
        }.getOrDefault(false)
    }

    /**
     * Transitions the session state safely.
     */
    fun transitionTo(nextState: HardwareSessionState) {
        if (isClosedFlag.get() && nextState !is HardwareSessionState.CleanTerminated && nextState !is HardwareSessionState.Failed) {
            Log.w(TAG, "Ignoring transition to $nextState because session is already closed/terminating")
            return
        }
        _state.value = nextState
        Log.d(TAG, "Session state -> $nextState")
    }

    /**
     * Suspends while executing deterministic 5-step clean teardown.
     */
    suspend fun disconnect(reason: String = "Normal disconnect") = withContext(Dispatchers.IO + NonCancellable) {
        if (!isClosedFlag.compareAndSet(false, true)) {
            Log.d(TAG, "disconnect() already executed for ${device.deviceName}")
            return@withContext
        }

        Log.i(TAG, "Beginning 5-step clean teardown for ${device.deviceName}. Reason: $reason")
        transitionTo(HardwareSessionState.Disconnecting(reason))

        withTimeoutOrNull(DEFAULT_TEARDOWN_TIMEOUT_MS) {
            // Step 1: Cancel session coroutines
            runCatching {
                sessionJob.cancel()
                Log.d(TAG, "Step 1: Cancelled session coroutines")
            }

            // Step 2: Drain in-flight USB IO latch
            runCatching {
                val drained = transportManager.beginDrainAndClose(DEFAULT_DRAIN_TIMEOUT_MS)
                Log.d(TAG, "Step 2: Drained transport manager IO latch (clean=$drained)")
            }

            // Step 3: Execute vendor-specific teardown callback
            runCatching {
                onVendorTeardown?.invoke()
                Log.d(TAG, "Step 3: Executed vendor teardown callback")
            }.onFailure {
                Log.w(TAG, "Vendor teardown callback encountered error", it)
            }

            // Step 4: Release all claimed USB interfaces
            for (iface in _claimedInterfaces) {
                runCatching {
                    connection.releaseInterface(iface)
                    Log.d(TAG, "Step 4: Released interface ${iface.id}")
                }.onFailure {
                    Log.w(TAG, "Failed releasing interface ${iface.id}", it)
                }
            }
            _claimedInterfaces.clear()

            // Step 5: Close UsbDeviceConnection
            runCatching {
                connection.close()
                Log.d(TAG, "Step 5: Closed UsbDeviceConnection")
            }.onFailure {
                Log.w(TAG, "Failed closing UsbDeviceConnection", it)
            }
        }

        transitionTo(HardwareSessionState.CleanTerminated)
        Log.i(TAG, "Hardware session for ${device.deviceName} terminated cleanly")
    }

    /**
     * Synchronous [AutoCloseable] implementation.
     */
    override fun close() {
        if (isClosedFlag.get()) return
        runBlocking {
            disconnect("AutoCloseable close()")
        }
    }
}
