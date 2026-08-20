package com.rvmeur.transferin.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TransferInDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate8To9() {
        // Create database with version 8
        var db = helper.createDatabase(TEST_DB, 8)

        // Insert some sample data using SQL queries (since Version 8 doesn't have uploaded_fingerprints, we can verify that other tables exist)
        db.execSQL(
            "INSERT INTO sessions (id, sourceTreeUri, targetNasDirPath, connectionMode, status, watcherStatus, " +
                    "acceptedFileCount, queuedFileCount, uploadingFileCount, successFileCount, failedFileCount, " +
                    "skippedFileCount, totalBytesDiscovered, totalBytesUploaded, startedAtEpochMs, createdAtEpochMs, " +
                    "updatedAtEpochMs, isActive) " +
                    "VALUES ('session-1', 'content://test', '/remote', 'SFTP', 'ACTIVE', 'IDLE', 0, 0, 0, 0, 0, 0, 0, 0, 1000, 1000, 1000, 1)"
        )
        db.close()

        // Re-open the database with version 9 and run Migration_8_9
        db = helper.runMigrationsAndValidate(TEST_DB, 9, true, Migration_8_9.MIGRATION_8_9)

        // Verify that the new table 'uploaded_fingerprints' exists and we can insert into it
        db.execSQL(
            "INSERT INTO uploaded_fingerprints (fingerprint, session_id, source_filename, file_size_bytes, nas_target_path, nas_filename, uploaded_at, ctg_handle, camera_device_id) " +
                    "VALUES ('fp-1', 'session-1', 'DSC0001.JPG', 1000, '/remote', 'DSC0001.JPG', 2000, 0, 'camera-1')"
        )

        // Verify we can query the new table
        val cursor = db.query("SELECT * FROM uploaded_fingerprints WHERE fingerprint = 'fp-1'")
        assert(cursor.moveToFirst())
        assert(cursor.getString(cursor.getColumnIndexOrThrow("session_id")) == "session-1")
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        var db = helper.createDatabase(TEST_DB, 9)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, Migration_9_10.MIGRATION_9_10)

        // Verify that camera_sync_items exists and can be inserted into
        db.execSQL(
            "INSERT INTO camera_sync_items (id, sessionId, vendorId, cameraSerialNumber, storageId, objectHandle, filename, fileSizeBytes, captureTimestampMs, compoundFingerprint, status, bytesUploaded, retryCount, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES ('item-1', 'session-1', 1227, 'CAM001', 1, 100, 'DSC0001.RAF', 50000, 1000, 'fp-1', 'DETECTED', 0, 0, 1000, 1000)"
        )

        val cursor = db.query("SELECT * FROM camera_sync_items WHERE id = 'item-1'")
        assert(cursor.moveToFirst())
        assert(cursor.getString(cursor.getColumnIndexOrThrow("filename")) == "DSC0001.RAF")
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        var db = helper.createDatabase(TEST_DB, 10)

        // Insert records into version 10 schema
        db.execSQL(
            "INSERT INTO camera_sync_items (id, sessionId, vendorId, cameraSerialNumber, storageId, objectHandle, filename, fileSizeBytes, captureTimestampMs, compoundFingerprint, status, bytesUploaded, retryCount, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES ('item-10', 'session-1', 1227, 'CAM001', 1, 100, 'DSC0001.RAF', 50000, 1000, 'fp-10', 'DETECTED', 0, 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO camera_media_items (id, deviceId, storageId, objectHandle, fileName, fileExtension, format, sizeBytes, modifiedAt, captureGroup, status, retryCount, discoveredAt) " +
                    "VALUES (1, 'dev-1', 1, 100, 'DSC0001.JPG', 'jpg', 12289, 5000, 1000, 'group-1', 'DISCOVERED', 0, 1000)"
        )
        db.close()

        // Run Migration 10 -> 11
        db = helper.runMigrationsAndValidate(TEST_DB, 11, true, Migration_10_11.MIGRATION_10_11)

        // Verify camera_sync_items columns and default values
        val syncCursor = db.query("SELECT * FROM camera_sync_items WHERE id = 'item-10'")
        assert(syncCursor.moveToFirst())
        assert(syncCursor.getString(syncCursor.getColumnIndexOrThrow("cullingVerdict")) == "OPTIMAL")
        assert(syncCursor.getDouble(syncCursor.getColumnIndexOrThrow("meanLuminance")) == 128.0)
        assert(syncCursor.getDouble(syncCursor.getColumnIndexOrThrow("entropyScore")) == 5.0)
        assert(syncCursor.getInt(syncCursor.getColumnIndexOrThrow("isCulled")) == 0)
        assert(syncCursor.getInt(syncCursor.getColumnIndexOrThrow("isManualUploadOverride")) == 0)
        assert(syncCursor.isNull(syncCursor.getColumnIndexOrThrow("thumbnailPath")))
        syncCursor.close()

        // Verify camera_media_items columns and default values
        val mediaCursor = db.query("SELECT * FROM camera_media_items WHERE id = 1")
        assert(mediaCursor.moveToFirst())
        assert(mediaCursor.getString(mediaCursor.getColumnIndexOrThrow("cullingVerdict")) == "OPTIMAL")
        assert(mediaCursor.getDouble(mediaCursor.getColumnIndexOrThrow("meanLuminance")) == 128.0)
        assert(mediaCursor.getDouble(mediaCursor.getColumnIndexOrThrow("entropyScore")) == 5.0)
        assert(mediaCursor.getInt(mediaCursor.getColumnIndexOrThrow("isCulled")) == 0)
        assert(mediaCursor.getInt(mediaCursor.getColumnIndexOrThrow("isManualUploadOverride")) == 0)
        mediaCursor.close()
    }
}
