package com.rvmeur.transferin.domain.repository

import com.rvmeur.transferin.data.local.entity.SessionEntity
import com.rvmeur.transferin.data.repository.SessionRepositoryImpl
import kotlinx.coroutines.flow.Flow

class SessionRepositoryAdapter(
    private val impl: SessionRepositoryImpl
) : SessionRepository {

    override suspend fun getActiveSessionOnce(): SessionEntity? {
        return impl.getActiveSessionOnce()
    }

    override fun observeActiveSession(): Flow<SessionEntity?> {
        return impl.observeActiveSession()
    }

    override suspend fun save(session: SessionEntity) {
        // Kita pakai insert(REPLACE) dari impl
        impl.createOrUpdateFromEntity(session)
    }

    override suspend fun deactivateAllSessions() {
        impl.deactivateAllSessions()
    }
}