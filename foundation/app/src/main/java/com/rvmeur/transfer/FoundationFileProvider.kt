package com.rvmeur.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Provider berkas mandiri (tanpa dependensi AndroidX) untuk membagikan berkas log foundation
 * dan berkas pembaruan APK.
 * Authority: com.rvmeur.transfer.fileprovider
 */
class FoundationFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment == "update") {
            val ctx = context ?: throw FileNotFoundException("Context null saat mengakses berkas update")
            val updateFile = File(ctx.cacheDir, "tether-update.apk")
            if (!updateFile.exists()) {
                throw FileNotFoundException("Berkas update APK tidak ditemukan: ${updateFile.absolutePath}")
            }
            return ParcelFileDescriptor.open(updateFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        val file = FoundationLog.currentLogFile
            ?: throw FileNotFoundException("Berkas log foundation belum diinisialisasi")
        if (!file.exists()) {
            throw FileNotFoundException("Berkas log foundation tidak ditemukan: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        return if (uri.lastPathSegment == "update") {
            "application/vnd.android.package-archive"
        } else {
            "text/plain"
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return try {
            val cursor = MatrixCursor(columns)
            if (uri.lastPathSegment == "update") {
                val ctx = context
                val updateFile = if (ctx != null) File(ctx.cacheDir, "tether-update.apk") else null
                val size = if (updateFile != null && updateFile.exists()) updateFile.length() else 0L
                cursor.addRow(arrayOf<Any>("tether-update.apk", size))
            } else {
                val file = FoundationLog.currentLogFile
                val name = file?.name ?: "transferin.log"
                val size = if (file != null && file.exists()) file.length() else 0L
                cursor.addRow(arrayOf<Any>(name, size))
            }
            cursor
        } catch (_: Throwable) {
            MatrixCursor(columns)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.rvmeur.transfer.fileprovider"

        fun logUri(): Uri = Uri.parse("content://$AUTHORITY/log")

        fun updateUri(): Uri = Uri.parse("content://$AUTHORITY/update")
    }
}
