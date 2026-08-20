package com.rvmeur.transferin.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 9 to 10.
 *
 * This migration adds the camera_sync_items table supporting the atomic
 * 5-stage Camera-to-Cloud sync pipeline.
 */
object Migration_9_10 {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS camera_sync_items (
                    id TEXT PRIMARY KEY NOT NULL,
                    sessionId TEXT NOT NULL,
                    vendorId INTEGER NOT NULL,
                    cameraSerialNumber TEXT NOT NULL,
                    storageId INTEGER NOT NULL,
                    objectHandle INTEGER NOT NULL,
                    filename TEXT NOT NULL,
                    fileSizeBytes INTEGER NOT NULL,
                    captureTimestampMs INTEGER NOT NULL,
                    compoundFingerprint TEXT NOT NULL,
                    status TEXT NOT NULL,
                    localFilePath TEXT,
                    remoteFileId TEXT,
                    bytesUploaded INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL,
                    lastErrorMessage TEXT,
                    createdAtEpochMs INTEGER NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    confirmedAtEpochMs INTEGER,
                    purgedAtEpochMs INTEGER
                )
                """.trimIndent()
            )

            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_camera_sync_items_sessionId ON camera_sync_items(sessionId)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_camera_sync_items_objectHandle ON camera_sync_items(objectHandle)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_camera_sync_items_compoundFingerprint ON camera_sync_items(compoundFingerprint)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_camera_sync_items_status ON camera_sync_items(status)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_camera_sync_items_confirmedAtEpochMs ON camera_sync_items(confirmedAtEpochMs)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_camera_sync_items_sessionId_status ON camera_sync_items(sessionId, status)"
            )
        }
    }
}
