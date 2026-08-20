package com.rvmeur.transferin.domain.sync

import android.util.Log
import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager
import com.rvmeur.transferin.data.upload.streaming.DirectStreamingEngine
import com.rvmeur.transferin.data.upload.streaming.DirectStreamingResult
import com.rvmeur.transferin.data.upload.streaming.UsbStreamRequestBody
import com.rvmeur.transferin.domain.camera.CameraPictureProfile
import com.rvmeur.transferin.domain.culling.CullingAction
import com.rvmeur.transferin.domain.culling.CullingDecisionGate
import com.rvmeur.transferin.domain.culling.CullingPolicy
import com.rvmeur.transferin.domain.culling.CullingVerdict
import com.rvmeur.transferin.domain.exposure.ExposureAnalysisResult
import com.rvmeur.transferin.domain.exposure.ExposureAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance coordinator connecting USB intake, preview JPEG extraction,
 * zero-alloc exposure analysis, smart culling decision gating, and streaming cloud uploads.
 */
class SyncOrchestrator(
    private val cameraSyncDao: CameraSyncDao,
    private val thumbnailStorageManager: ThumbnailStorageManager? = null,
    private val exposureAnalyzer: ExposureAnalyzer = ExposureAnalyzer,
    private val cullingDecisionGate: CullingDecisionGate = CullingDecisionGate(),
    private val directStreamingEngine: DirectStreamingEngine? = null,
    private val syncTracker: CameraSyncTracker? = null
) {
    private val totalQuotaSavedBytes = AtomicLong(0L)

    val quotaSavedBytes: Long
        get() = totalQuotaSavedBytes.get()

    /**
     * Processes a newly discovered or incoming camera frame through the full culling and sync pipeline.
     *
     * @param syncItem The database entity representing the camera item
     * @param previewBytes Optional preview JPEG byte array extracted from camera/file
     * @param pictureProfile Camera picture profile or film simulation for exposure analysis
     * @param sessionPolicy The culling policy for the current photoshoot session
     * @param isManualOverride Flag indicating if this was triggered via manual user override
     * @param fallbackSource Function to write raw stream data if needed
     * @param directUploader Function to upload via OkHttp stream
     */
    suspend fun processFrame(
        syncItem: CameraSyncItemEntity,
        previewBytes: ByteArray? = null,
        pictureProfile: CameraPictureProfile = CameraPictureProfile.STANDARD_SRGB,
        sessionPolicy: CullingPolicy = CullingPolicy.AUTO_SKIP_UPLOAD,
        isManualOverride: Boolean = false,
        fallbackSource: (suspend (OutputStream) -> Unit)? = null,
        directUploader: (suspend (UsbStreamRequestBody) -> String)? = null
    ): FrameProcessingResult = withContext(Dispatchers.IO) {
        var localThumbPath: String? = syncItem.thumbnailPath
        var analysisResult: ExposureAnalysisResult? = null
        var verdict = CullingVerdict.OPTIMAL
        var meanLum = syncItem.meanLuminance
        var entropy = syncItem.entropyScore

        // 1. Save thumbnail if preview bytes provided
        if (previewBytes != null && previewBytes.isNotEmpty() && thumbnailStorageManager != null) {
            val thumbFile = thumbnailStorageManager.saveThumbnail(syncItem.compoundFingerprint, previewBytes)
            localThumbPath = thumbFile?.absolutePath
        }

        // 2. Perform exposure analysis if preview bytes provided
        if (previewBytes != null && previewBytes.isNotEmpty()) {
            analysisResult = exposureAnalyzer.analyzeRgbBytes(
                rgbBytes = previewBytes,
                profile = pictureProfile
            )
            verdict = CullingVerdict.fromClassification(analysisResult.classification)
            meanLum = analysisResult.meanLuminance.toDouble()
            entropy = analysisResult.shannonEntropy.toDouble()
        }

        // 3. Evaluate Culling Decision Gate
        val action = cullingDecisionGate.evaluate(
            verdict = verdict,
            policy = sessionPolicy,
            isManualOverride = isManualOverride || syncItem.isManualUploadOverride
        )

        when (action) {
            CullingAction.SKIP_UPLOAD_SAVING_QUOTA -> {
                cameraSyncDao.markCulledSkipped(
                    id = syncItem.id,
                    verdict = verdict.name,
                    meanLum = meanLum,
                    entropy = entropy,
                    thumbPath = localThumbPath
                )
                totalQuotaSavedBytes.addAndGet(syncItem.fileSizeBytes)
                Log.i(TAG, "Frame ${syncItem.filename} culled ($verdict). Tier 2 RAW transfer skipped, saving ${syncItem.fileSizeBytes} B.")
                return@withContext FrameProcessingResult.CulledSkipped(
                    syncItem = syncItem.copy(
                        status = CameraSyncStatus.CULLED_SKIPPED,
                        cullingVerdict = verdict.name,
                        meanLuminance = meanLum,
                        entropyScore = entropy,
                        isCulled = true,
                        thumbnailPath = localThumbPath
                    ),
                    verdict = verdict,
                    quotaSavedBytes = syncItem.fileSizeBytes
                )
            }

            CullingAction.FLAG_WARNING_ONLY,
            CullingAction.PROCEED_UPLOAD -> {
                val isCulledFlag = (action == CullingAction.FLAG_WARNING_ONLY)
                // Proceed with upload
                if (directStreamingEngine != null && fallbackSource != null && directUploader != null) {
                    cameraSyncDao.transitionStatus(
                        id = syncItem.id,
                        expectedOldStatus = syncItem.status,
                        newStatus = CameraSyncStatus.STREAMING_CLOUD
                    )

                    try {
                        val streamResult = directStreamingEngine.streamWithFallback(
                            fingerprint = syncItem.compoundFingerprint,
                            fileExtension = syncItem.filename.substringAfterLast('.', "dat"),
                            expectedLength = syncItem.fileSizeBytes,
                            onProgress = { transferred, _ ->
                                syncTracker?.recordBytesTransferred(transferred)
                            },
                            fallbackSource = fallbackSource,
                            directUploader = directUploader
                        )

                        when (streamResult) {
                            is DirectStreamingResult.DirectSuccess -> {
                                cameraSyncDao.markConfirmedCloud(
                                    id = syncItem.id,
                                    remoteFileId = streamResult.remoteFileId
                                )
                                return@withContext FrameProcessingResult.Uploaded(
                                    remoteFileId = streamResult.remoteFileId,
                                    bytesUploaded = streamResult.bytesStreamed,
                                    isWarning = isCulledFlag
                                )
                            }
                            is DirectStreamingResult.FallbackBuffered -> {
                                cameraSyncDao.markBufferedLocal(
                                    id = syncItem.id,
                                    localFilePath = streamResult.transitFile.absolutePath
                                )
                                return@withContext FrameProcessingResult.BufferedLocal(
                                    transitFilePath = streamResult.transitFile.absolutePath,
                                    isWarning = isCulledFlag
                                )
                            }
                            is DirectStreamingResult.Failed -> {
                                cameraSyncDao.markFailed(
                                    id = syncItem.id,
                                    errorMessage = streamResult.error.message ?: "Streaming upload failed"
                                )
                                return@withContext FrameProcessingResult.Failed(streamResult.error)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        cameraSyncDao.markFailed(
                            id = syncItem.id,
                            errorMessage = t.message ?: "Upload exception"
                        )
                        return@withContext FrameProcessingResult.Failed(t)
                    }
                } else {
                    return@withContext FrameProcessingResult.ProceededWithoutUpload(
                        verdict = verdict,
                        isWarning = isCulledFlag
                    )
                }
            }
        }
    }

    /**
     * Executes manual 1-tap "Paksa Unggah" override on a culled or skipped item.
     */
    suspend fun forceUpload(
        itemId: String,
        fallbackSource: (suspend (OutputStream) -> Unit)? = null,
        directUploader: (suspend (UsbStreamRequestBody) -> String)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        cameraSyncDao.markForceUploadOverride(itemId)
        val item = cameraSyncDao.getById(itemId) ?: return@withContext false

        if (directStreamingEngine != null && fallbackSource != null && directUploader != null) {
            cameraSyncDao.transitionStatus(
                id = item.id,
                expectedOldStatus = CameraSyncStatus.FORCE_UPLOAD_PENDING,
                newStatus = CameraSyncStatus.STREAMING_CLOUD
            )

            try {
                val streamResult = directStreamingEngine.streamWithFallback(
                    fingerprint = item.compoundFingerprint,
                    fileExtension = item.filename.substringAfterLast('.', "dat"),
                    expectedLength = item.fileSizeBytes,
                    fallbackSource = fallbackSource,
                    directUploader = directUploader
                )
                if (streamResult is DirectStreamingResult.DirectSuccess) {
                    cameraSyncDao.markConfirmedCloud(item.id, streamResult.remoteFileId)
                    return@withContext true
                }
            } catch (t: Throwable) {
                cameraSyncDao.markFailed(item.id, t.message ?: "Force upload failed")
                return@withContext false
            }
        }
        return@withContext true
    }

    companion object {
        private const val TAG = "SyncOrchestrator"
    }
}

sealed interface FrameProcessingResult {
    data class CulledSkipped(
        val syncItem: CameraSyncItemEntity,
        val verdict: CullingVerdict,
        val quotaSavedBytes: Long
    ) : FrameProcessingResult

    data class Uploaded(
        val remoteFileId: String,
        val bytesUploaded: Long,
        val isWarning: Boolean = false
    ) : FrameProcessingResult

    data class BufferedLocal(
        val transitFilePath: String,
        val isWarning: Boolean = false
    ) : FrameProcessingResult

    data class ProceededWithoutUpload(
        val verdict: CullingVerdict,
        val isWarning: Boolean = false
    ) : FrameProcessingResult

    data class Failed(
        val error: Throwable
    ) : FrameProcessingResult
}
