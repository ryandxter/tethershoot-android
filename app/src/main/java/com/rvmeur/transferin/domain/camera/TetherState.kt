package com.rvmeur.transferin.domain.camera

/**
 * Deterministic lifecycle states for camera tether connections.
 */
sealed class TetherState {
    object Idle : TetherState()
    object Connecting : TetherState()
    data class Handshake(val stage: String) : TetherState()
    data class ActiveStreaming(val activeTransfers: Int = 0) : TetherState()
    data class Disconnecting(val reason: String) : TetherState()
    object CleanTerminated : TetherState()
    data class Failed(val error: Throwable) : TetherState()
}
