package com.rvmeur.transferin.domain.session

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bridge untuk file baru yang ditemukan oleh source/orchestrator.
 *
 * Karakteristik:
 * - Hot stream
 * - Tidak menyimpan replay untuk subscriber baru
 * - Cocok untuk live pipeline yang collector-nya sudah aktif
 * - Bukan persistent queue / durable event store
 */
class SourceWatcher {

    companion object {
        private const val TAG = "SourceWatcher"
        private const val BUFFER_CAPACITY = 64
    }

    /**
     * replay = 0:
     * - Subscriber baru tidak menerima event lama.
     *
     * extraBufferCapacity = 64:
     * - Menyerap burst kecil saat emitter lebih cepat dari collector.
     *
     * onBufferOverflow = SUSPEND:
     * - Jangan drop event diam-diam saat collector aktif tapi lebih lambat.
     * - Emitter akan suspend kalau buffer penuh.
     */
    private val _newFiles = MutableSharedFlow<DiscoveredFile>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val newFiles: Flow<DiscoveredFile> = _newFiles.asSharedFlow()

    /**
     * Emit file baru secara suspend.
     * Dipakai untuk alur utama live import agar event tidak gampang hilang.
     */
    suspend fun emitNewFile(file: DiscoveredFile) {
        val normalized = file.normalized()

        Log.d(
            TAG,
            "emitNewFile() start: fileName=${normalized.fileName}, fullPath=${normalized.fullPath}, sizeBytes=${normalized.sizeBytes}, lastModified=${normalized.lastModifiedEpochMs}"
        )

        _newFiles.emit(normalized)

        Log.d(
            TAG,
            "emitNewFile() success: fileName=${normalized.fileName}, fullPath=${normalized.fullPath}"
        )
    }

    /**
     * Emit non-blocking.
     * Hanya dipakai jika caller siap menerima kemungkinan event tidak masuk
     * ketika buffer sedang penuh.
     */
    fun tryEmitNewFile(file: DiscoveredFile): Boolean {
        val normalized = file.normalized()

        Log.d(
            TAG,
            "tryEmitNewFile() attempt: fileName=${normalized.fileName}, fullPath=${normalized.fullPath}, sizeBytes=${normalized.sizeBytes}, lastModified=${normalized.lastModifiedEpochMs}"
        )

        val emitted = _newFiles.tryEmit(normalized)

        Log.d(
            TAG,
            "tryEmitNewFile() result=$emitted: fileName=${normalized.fileName}, fullPath=${normalized.fullPath}"
        )

        return emitted
    }
}

data class DiscoveredFile(
    val fullPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long
) {
    fun normalized(): DiscoveredFile {
        val normalizedFullPath = fullPath.trim()
        val normalizedFileName = fileName.trim()

        require(normalizedFullPath.isNotBlank()) {
            "DiscoveredFile.fullPath must not be blank"
        }
        require(normalizedFileName.isNotBlank()) {
            "DiscoveredFile.fileName must not be blank"
        }

        val normalized = copy(
            fullPath = normalizedFullPath,
            fileName = normalizedFileName,
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            lastModifiedEpochMs = lastModifiedEpochMs.coerceAtLeast(0L)
        )

        Log.d(
            "DiscoveredFile",
            "normalized(): originalFullPath=$fullPath, normalizedFullPath=${normalized.fullPath}, originalFileName=$fileName, normalizedFileName=${normalized.fileName}, normalizedSize=${normalized.sizeBytes}, normalizedLastModified=${normalized.lastModifiedEpochMs}"
        )

        return normalized
    }
}