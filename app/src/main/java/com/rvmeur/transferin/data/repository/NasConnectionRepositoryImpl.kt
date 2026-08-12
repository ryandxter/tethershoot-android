package com.rvmeur.transferin.data.repository

import com.rvmeur.transferin.data.local.dao.NasConnectionDao
import com.rvmeur.transferin.data.local.entity.NasConnectionEntity
import com.rvmeur.transferin.domain.repository.NasConnection
import com.rvmeur.transferin.domain.repository.NasConnectionRepository

class NasConnectionRepositoryImpl(
    private val dao: NasConnectionDao
) : NasConnectionRepository {

    override suspend fun getConnection(): NasConnection? {
        return dao.getConnectionOrNull()?.toDomain()
    }

    override suspend fun saveConnection(connection: NasConnection) {
        dao.upsert(connection.toEntity())
    }

    override suspend fun updateDefaultRemoteFolder(folder: String) {
        dao.updateDefaultRemoteFolder(folder.trim())
    }

    override suspend fun clearConnection() {
        dao.deleteConnection()
    }

    private fun NasConnectionEntity.toDomain(): NasConnection {
        return NasConnection(
            name = name,
            host = host,
            port = port,
            username = username,
            password = password,
            defaultRemoteFolder = defaultRemoteFolder,
            protocol = protocol,
            useHttps = useHttps,
            startingFolder = startingFolder
        )
    }

    private fun NasConnection.toEntity(): NasConnectionEntity {
        return NasConnectionEntity(
            name = name,
            host = host,
            port = port,
            username = username,
            password = password,
            defaultRemoteFolder = defaultRemoteFolder,
            protocol = protocol,
            useHttps = useHttps,
            startingFolder = startingFolder
        )
    }
}