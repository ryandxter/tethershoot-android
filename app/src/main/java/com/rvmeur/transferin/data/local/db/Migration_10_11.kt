package com.rvmeur.transferin.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 10 to 11.
 *
 * Adds culling analysis metadata (cullingVerdict, meanLuminance, entropyScore,
 * isCulled, isManualUploadOverride) and persistent thumbnailPath to camera_sync_items
 * and camera_media_items.
 */
object Migration_10_11 {
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Update camera_sync_items
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN thumbnailPath TEXT")
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN cullingVerdict TEXT NOT NULL DEFAULT 'OPTIMAL'")
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN meanLuminance REAL NOT NULL DEFAULT 128.0")
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN entropyScore REAL NOT NULL DEFAULT 5.0")
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN isCulled INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE camera_sync_items ADD COLUMN isManualUploadOverride INTEGER NOT NULL DEFAULT 0")

            // Update camera_media_items
            database.execSQL("ALTER TABLE camera_media_items ADD COLUMN cullingVerdict TEXT NOT NULL DEFAULT 'OPTIMAL'")
            database.execSQL("ALTER TABLE camera_media_items ADD COLUMN meanLuminance REAL NOT NULL DEFAULT 128.0")
            database.execSQL("ALTER TABLE camera_media_items ADD COLUMN entropyScore REAL NOT NULL DEFAULT 5.0")
            database.execSQL("ALTER TABLE camera_media_items ADD COLUMN isCulled INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE camera_media_items ADD COLUMN isManualUploadOverride INTEGER NOT NULL DEFAULT 0")
        }
    }
}
