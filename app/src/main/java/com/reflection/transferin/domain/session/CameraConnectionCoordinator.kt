package com.reflection.transferin.domain.session

import com.reflection.transferin.domain.model.CameraRuntimeState
import com.reflection.transferin.domain.model.ConnectionMode
import com.reflection.transferin.domain.model.ProjectSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mengkoordinasikan pemilihan engine kamera berdasarkan ProjectSession dan ConnectionMode.
 * Untuk saat ini, selalu memilih ImportCameraEngine sebagai fallback yang sudah tersedia.
 */
class CameraConnectionCoordinator(
    private val importEngine: CameraEngine
    // nanti bisa ditambah: private val tetheredEngine: CameraEngine
) {

    private val _runtimeState = MutableStateFlow(CameraRuntimeState())
    val runtimeState: StateFlow<CameraRuntimeState> = _runtimeState

    private var currentEngine: CameraEngine? = null
    private var currentSession: ProjectSession? = null

    fun connect(session: ProjectSession) {
        currentSession = session

        // untuk sekarang, pilih selalu import engine
        val mode = session.connectionMode
        val engine = selectEngineForMode(mode)

        currentEngine = engine

        // update state awal
        _runtimeState.value = CameraRuntimeState(
            isConnected = false,
            canShoot = false,
            isReceiving = false,
            activeMode = mode,
            statusText = "Menghubungkan kamera (${mode.name})..."
        )

        engine.connect(session)
    }

    fun disconnect() {
        val engine = currentEngine ?: return
        engine.disconnect()
        currentEngine = null

        _runtimeState.value = CameraRuntimeState(
            isConnected = false,
            canShoot = false,
            isReceiving = false,
            activeMode = null,
            statusText = "Kamera terputus."
        )
    }

    fun refresh() {
        currentEngine?.refresh()
    }

    private fun selectEngineForMode(mode: ConnectionMode): CameraEngine {
        // TODO: nanti tambahkan logika untuk memilih TetheredEngine.
        // Saat ini, selalu importEngine.
        return importEngine
    }
}