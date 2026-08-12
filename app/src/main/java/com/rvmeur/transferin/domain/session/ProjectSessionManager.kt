package com.rvmeur.transferin.domain.session

import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.ProjectSession
import kotlinx.coroutines.flow.Flow

interface ProjectSessionManager {

    val activeSession: Flow<ProjectSession?>

    suspend fun createOrUpdateSession(
        sessionId: String,
        sessionLabel: String?,
        customerName: String?,
        targetNasPath: String,
        sourceFolderPath: String,
        connectionMode: ConnectionMode
    )

    suspend fun setConnectionMode(connectionMode: ConnectionMode)

    suspend fun setActive(isActive: Boolean)

    /**
     * Deactivate ALL sessions. Called before creating a new session
     * to prevent old sessions from being picked up.
     */
    suspend fun deactivateAllSessions()
}