package com.rvmeur.transferin.domain.repository

data class NasConnection(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val defaultRemoteFolder: String?,
    val protocol: String = "SFTP",
    val useHttps: Boolean = false,
    val startingFolder: String? = null
)

interface NasConnectionRepository {

    suspend fun getConnection(): NasConnection?

    suspend fun saveConnection(connection: NasConnection)

    suspend fun updateDefaultRemoteFolder(folder: String)
}