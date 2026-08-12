package com.rvmeur.transferin.data.sftp

import android.net.Uri
import com.rvmeur.transferin.domain.session.RemoteFolder

data class TmpFileInfo(
    val name: String,
    val fullPath: String,
    val size: Long,
    val modifiedAt: Long
)

interface SftpClient {

    /**
     * Menguji koneksi ke NAS via SFTP.
     * Tidak upload apa pun, hanya coba login dan disconnect.
     */
    suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit>

    /**
     * Mengambil path awal setelah login.
     * Biasanya home directory user SFTP atau base path yang kamu tetapkan.
     */
    suspend fun getInitialPath(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String>

    /**
     * Menampilkan daftar folder pada path tertentu.
     * Fokus folder saja dulu untuk MVP.
     */
    suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>>

    /**
     * Membuat folder baru pada parent path tertentu.
     */
    suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String
    ): Result<String>

    /**
     * Upload satu file dari Uri lokal (mis. SAF / treeUri kamera) ke folder NAS tujuan.
     *
     * remoteDirectory: folder tujuan di NAS, contoh "/photo/job-001"
     * remoteFileName: nama file di NAS, contoh "DSC0001.JPG"
     *
     * onProgress akan dipanggil jika implementasi bisa menghitung progress.
     * Parameter:
     * - bytesTransferred: total byte yang sudah terkirim
     * - totalBytes: ukuran total file jika diketahui, 0 bila tidak diketahui
     */
    suspend fun uploadFileFromUri(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit>

    suspend fun uploadWithTwoPhaseCommit(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        expectedSize: Long,
        startOffset: Long,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit>

    suspend fun deleteTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit>

    suspend fun listTmpFiles(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<List<TmpFileInfo>>

    /**
     * Memeriksa apakah user memiliki hak akses tulis (RW) pada direktori tujuan SFTP.
     */
    suspend fun checkWritePermission(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String
    ): Result<Boolean>
}