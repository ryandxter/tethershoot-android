package com.rvmeur.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Provider berkas mandiri (tanpa dependensi AndroidX) untuk membagikan berkas log foundation.
 * Authority: com.rvmeur.transfer.fileprovider
 */
class FoundationFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = FoundationLog.currentLogFile
            ?: throw FileNotFoundException("Berkas log foundation belum diinisialisasi")
        if (!file.exists()) {
            throw FileNotFoundException("Berkas log foundation tidak ditemukan: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

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
    }
}
