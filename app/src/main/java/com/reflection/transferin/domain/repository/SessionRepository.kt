package com.reflection.transferin.domain.repository

import com.reflection.transferin.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    suspend fun getActiveSessionOnce(): SessionEntity?

    fun observeActiveSession(): Flow<SessionEntity?>

    suspend fun save(session: SessionEntity)

    /**
     * Deactivate ALL sessions. Used to clean up before creating a new session.
     */
    suspend fun deactivateAllSessions()
}