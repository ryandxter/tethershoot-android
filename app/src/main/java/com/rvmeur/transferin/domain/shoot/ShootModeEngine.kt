package com.rvmeur.transferin.domain.shoot

import android.content.Context
import android.util.Log
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.model.CameraRuntimeState
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.MediaJob
import com.rvmeur.transferin.domain.model.ProjectSession
import com.rvmeur.transferin.domain.session.CameraEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TetheredEngine - Mode Utama (Tanpa Lock Kamera)
 *
 * PRINSIP: TIDAK membuka MTP session. Kamera tetap bisa motret.
 * - Monitor koneksi USB tanpa lock
 * - Lifecycle coroutine terstruktur dengan SupervisorJob
 */
class ShootModeEngine(
    private val appContext: Context,
    private val usbHelper: UsbHelper,
    private val usbSourceOrchestrator: UsbSourceOrchestrator
) : CameraEngine {

    companion object {
        private const val TAG = "TetheredEngine"
    }

    private val engineJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + engineJob)

    private val _runtimeState = MutableStateFlow(CameraRuntimeState(statusText = "Ready"))
    override val runtimeState: StateFlow<CameraRuntimeState> = _runtimeState.asStateFlow()

    private val _incomingMedia = MutableSharedFlow<MediaJob>(extraBufferCapacity = 64)
    override val incomingMedia = _incomingMedia.asSharedFlow()

    private var monitorJob: Job? = null
    private var currentSession: ProjectSession? = null
    private val isEngineActive = AtomicBoolean(false)

    override fun connect(projectSession: ProjectSession) {
        Log.d(TAG, ">>> connect() called with session: ${projectSession.id}, mode: ${projectSession.connectionMode}")
        currentSession = projectSession
        isEngineActive.set(true)

        _runtimeState.value = CameraRuntimeState(
            isConnected = true,
            canShoot = true,
            activeMode = ConnectionMode.TETHERED,
            statusText = "TETHERED: Kamera siap motret"
        )

        startTetherMode()
        Log.d(TAG, ">>> startTetherMode() called")
    }

    /**
     * TETHERED MODE: Tanpa MTP session.
     * Kamera dalam mode normal, siap motret.
     * App memantau konektivitas hardware USB.
     */
    private fun startTetherMode() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            Log.d(TAG, "=== TETHERED MODE START ===")

            _runtimeState.value = _runtimeState.value.copy(
                canShoot = true,
                statusText = "TETHERED: Kamera siap motret!"
            )

            while (isActive && isEngineActive.get()) {
                try {
                    val hasDevice = usbHelper.monitorState.value.cameraLikeDevices.isNotEmpty()

                    if (hasDevice) {
                        _runtimeState.value = _runtimeState.value.copy(
                            isConnected = true,
                            canShoot = true,
                            statusText = "TETHERED: Kamera terhubung, siap motret!"
                        )
                    } else {
                        _runtimeState.value = _runtimeState.value.copy(
                            isConnected = false,
                            statusText = "TETHERED: Kamera terputus"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Tether poll error", e)
                }
                delay(3000)
            }
        }
    }

    override fun disconnect() {
        isEngineActive.set(false)
        monitorJob?.cancel()
        monitorJob = null
        engineJob.cancelChildren()
        _runtimeState.value = CameraRuntimeState(
            statusText = "TETHERED: Terputus"
        )
    }

    override fun refresh() {
        usbHelper.refresh()
    }

    fun stop() {
        disconnect()
        engineJob.cancel()
    }
}
