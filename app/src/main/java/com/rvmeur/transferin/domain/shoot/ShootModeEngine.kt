package com.rvmeur.transferin.domain.shoot

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.model.CameraRuntimeState
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.MediaJob
import com.rvmeur.transferin.domain.model.ProjectSession
import com.rvmeur.transferin.domain.model.UploadStatus
import com.rvmeur.transferin.domain.session.CameraEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TetheredEngine - Mode Utama (Tanpa Lock Kamera)
 *
 * PRINSIP: TIDAK membuka MTP session. Kamera tetap bisa motret.
 * - Monitor koneksi USB tanpa lock
 * - Polling ringan untuk deteksi foto baru
 * - Tidak akses storage kamera (yang menyebabkan lock)
 *
 * ImportEngine = Fallback (ada lock, tapi bisa scan storage)
 */
class ShootModeEngine(
    private val appContext: Context,
    private val usbHelper: UsbHelper,
    private val usbSourceOrchestrator: UsbSourceOrchestrator
) : CameraEngine {

    companion object {
        private const val TAG = "TetheredEngine"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _runtimeState = MutableStateFlow(CameraRuntimeState(statusText = "Ready"))
    override val runtimeState: StateFlow<CameraRuntimeState> = _runtimeState.asStateFlow()

    private val _incomingMedia = MutableSharedFlow<MediaJob>(extraBufferCapacity = 64)
    override val incomingMedia = _incomingMedia.asSharedFlow()

    private var monitorJob: Job? = null
    private var currentSession: ProjectSession? = null
    private val isEngineActive = AtomicBoolean(false)

    override fun connect(projectSession: ProjectSession) {
        android.util.Log.d("TetheredEngine", ">>> connect() called with session: ${projectSession.id}, mode: ${projectSession.connectionMode}")
        currentSession = projectSession
        isEngineActive.set(true)

        _runtimeState.value = CameraRuntimeState(
            isConnected = true,
            canShoot = true,  // Kunci: kamera tetap bisa motret
            activeMode = ConnectionMode.TETHERED,
            statusText = "TETHERED: Kamera siap motret"
        )

        // TETHERED mode: Jangan buka MTP session!
        // Cukup monitor koneksi USB
        startTetherMode()
        android.util.Log.d("TetheredEngine", ">>> startTetherMode() called")
    }

    /**
     * TETHERED MODE: Tanpa MTP session.
     * Kamera dalam mode normal, bisa motret.
     * App hanya monitor, tidak akses storage.
     */
    private fun startTetherMode() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            Log.d(TAG, "=== TETHERED MODE START (NO MTP LOCK) ===")
            Log.d(TAG, "Camera should be in normal shooting mode")

            // TIDAK buka MTP session!
            // MTP session menyebabkan kamera lock

            _runtimeState.value = _runtimeState.value.copy(
                canShoot = true,
                statusText = "TETHERED: Kamera siap motret!"
            )

            // Monitor koneksi USB tanpa akses storage
            while (isActive && isEngineActive.get()) {
                try {
                    // Cek apakah USB device masih terhubung
                    val hasDevice = usbHelper.monitorState.value.cameraLikeDevices.isNotEmpty()

                    if (hasDevice) {
                        _runtimeState.value = _runtimeState.value.copy(
                            isConnected = true,
                            canShoot = true,  // Tetap bisa motret
                            statusText = "TETHERED: Kamera terhubung, siap motret!"
                        )

                        // Untuk deteksi foto baru di mode tethered,
                        // kita perlu pendekatan berbeda:
                        // 1. Cek apakah ada file baru via MediaStore
                        // 2. Atau gunakan polling ringan ke storage
                        checkForNewPhotosViaMediaStore()
                    } else {
                        _runtimeState.value = _runtimeState.value.copy(
                            isConnected = false,
                            statusText = "TETHERED: Kamera terputus"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Tether poll error", e)
                }
                delay(3000) // Poll setiap 3 detik
            }
        }
    }

    /**
     * Cek foto baru via MediaStore (tanpa lock kamera).
     * Ini tidak menyebabkan lock karena tidak akses MTP langsung.
     */
    private suspend fun checkForNewPhotosViaMediaStore() {
        withContext(Dispatchers.IO) {
            try {
                // Cek recent photos via MediaStore
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    android.provider.MediaStore.Images.Media.SIZE
                )

                val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"

                appContext.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                        val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                        val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE)

                        // Ambil 1 foto terbaru
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val date = cursor.getLong(dateColumn)
                        val size = cursor.getLong(sizeColumn)

                        // Cek apakah ini foto baru (dalam 60 detik terakhir)
                        val now = System.currentTimeMillis() / 1000
                        if (now - date < 60) {
                            Log.d(TAG, ">>> NEW PHOTO DETECTED: $name")

                            val mediaJob = MediaJob(
                                id = UUID.randomUUID().toString(),
                                projectId = currentSession?.id ?: "",
                                localTempPath = "",
                                originalFileName = name,
                                sourceUri = "content://media/external/images/media/$id",
                                sourceFingerprint = "$name|$id",
                                fileSizeBytes = size,
                                lastModifiedEpochMs = date * 1000,
                                createdAtEpochMs = System.currentTimeMillis(),
                                uploadStatus = UploadStatus.QUEUED
                            )

                            _incomingMedia.emit(mediaJob)

                            _runtimeState.value = _runtimeState.value.copy(
                                isReceiving = true,
                                statusText = "TETHERED: Foto baru diterima!"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore query error", e)
            }
        }
    }

    override fun disconnect() {
        isEngineActive.set(false)
        monitorJob?.cancel()

        // TIDAK tutup MTP session karena kita tidak buka
        _runtimeState.value = CameraRuntimeState(
            statusText = "TETHERED: Terputus"
        )
    }

    override fun refresh() {
        // Refresh USB state tanpa akses MTP
        usbHelper.refresh()
    }

    fun stop() {
        disconnect()
    }
}
