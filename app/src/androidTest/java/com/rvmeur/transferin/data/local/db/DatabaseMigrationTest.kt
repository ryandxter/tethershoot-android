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
}
