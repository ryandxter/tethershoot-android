package com.rvmeur.transferin.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 8 to 9.
 *
 * This migration adds the uploaded_fingerprints table for cross-session
 * deduplication. It stores fingerprints of successfully uploaded files
 * to enable O(1) duplicate detection across app restarts.
 */
object Migration_8_9 {
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create uploaded_fingerprints table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS uploaded_fingerprints (
                    fingerprint TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    source_filename TEXT NOT NULL,
                    file_size_bytes INTEGER NOT NULL,
                    nas_target_path TEXT NOT NULL,
                    nas_filename TEXT NOT NULL,
                    uploaded_at INTEGER NOT NULL,
                    ctg_handle INTEGER NOT NULL,
                    camera_device_id TEXT
                )
            """.trimIndent())

            // Create index on session_id for session-based queries
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_fingerprints_session " +
                "ON uploaded_fingerprints(session_id)"
            )

            // Create index on uploaded_at for cleanup operations
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_fingerprints_uploaded_at " +
                "ON uploaded_fingerprints(uploaded_at)"
            )

            // Create composite index for session + time queries
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_fingerprints_session_time " +
                "ON uploaded_fingerprints(session_id, uploaded_at)"
            )
        }
    }
}
