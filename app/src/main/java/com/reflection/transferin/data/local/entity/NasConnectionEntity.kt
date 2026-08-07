package com.reflection.transferin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nas_connection")
data class NasConnectionEntity(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1,              // selalu 1 = current connection

    val name: String,             // "Studio A NAS"
    val host: String,             // "192.168.1.10"
    val port: Int = 22,           // default SFTP port
    val username: String,         // "andi_photo"
    val password: String,         // sementara plain, nanti bisa dienkripsi
    val defaultRemoteFolder: String? = null, // "/photo/studio-a/andi"
    val protocol: String = "SFTP",
    val useHttps: Boolean = false,
    val startingFolder: String? = null
)