package com.rvmeur.transferin.feature.sessionactive

import com.rvmeur.transferin.domain.model.ConnectionMode

/**
 * Stats for the session
 */
data class SessionStats(
    val pending: Int = 0,
    val uploading: Int = 0,
    val success: Int = 0,
    val failed: Int = 0
)

/**
 * Summary state for the session
 */
data class SessionSummaryState(
    val customerName: String? = null,
    val nasFolderPath: String = "",
    val sourceTreeUri: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val connectionMode: ConnectionMode = ConnectionMode.IMPORT_FALLBACK
)

/**
 * Shoot mode state for the session
 */
data class ShootModeState(
    val isActive: Boolean = false,
    val isBaselineReady: Boolean = false,
    val isScanning: Boolean = false,
    val baselineFileCount: Int = 0,
    val uploadedCount: Int = 0,
    val statusText: String = "Siap.",
    val storageName: String? = null,
    val reconnectionStatusText: String? = null
)

/**
 * Tether state for the session
 */
data class TetherState(
    val isConnected: Boolean = false,
    val isUnlocked: Boolean = false,
    val isWatching: Boolean = false,
    val detectedCount: Int = 0,
    val statusText: String = "Siap.",
    val storageName: String? = null
)
