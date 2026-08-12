package com.rvmeur.transferin.domain.model

data class CameraRuntimeState(
    val isConnected: Boolean = false,
    val canShoot: Boolean = false,
    val isReceiving: Boolean = false,
    val isUnlocked: Boolean = false,
    val activeMode: ConnectionMode? = null,
    val statusText: String = ""
)
