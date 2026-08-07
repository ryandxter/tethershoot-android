package com.reflection.transferin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["sourceTreeUri"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,

    // Label/nama proyek & customer (boleh null)
    val sessionLabel: String?,
    val customerName: String?,

    // Sumber & target NAS
    val sourceTreeUri: String,
    val targetNasDirPath: String,

    // Mode koneksi (TETHERED atau IMPORT_FALLBACK disimpan sebagai String)
    val connectionMode: String,

    // Status sesi & watcher
    val status: String,
    val watcherStatus: String,

    // Statistik file
    val acceptedFileCount: Int,
    val queuedFileCount: Int,
    val uploadingFileCount: Int,
    val successFileCount: Int,
    val failedFileCount: Int,
    val skippedFileCount: Int,

    // Statistik bytes
    val totalBytesDiscovered: Long,
    val totalBytesUploaded: Long,

    // Waktu scanning & lifecycle
    val lastScannedAtEpochMs: Long?,
    val startedAtEpochMs: Long,
    val intakeStoppedAtEpochMs: Long?,
    val completedAtEpochMs: Long?,

    // Waktu audit
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    // Flag aktif eksplisit untuk ProjectSessionManager
    val isActive: Boolean
) {
    val isActiveLike: Boolean
        get() = status == "STARTING" ||
                status == "RUNNING" ||
                status == "STOPPING_INTAKE" ||
                status == "UPLOAD_DRAINING"
}