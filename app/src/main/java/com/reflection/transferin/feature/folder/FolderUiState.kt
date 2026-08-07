package com.reflection.transferin.feature.folder

import com.reflection.transferin.data.usb.UsbSourceOrchestrator
import com.reflection.transferin.domain.session.RemoteFolder

data class FolderUiState(
    val isLoading: Boolean = false,
    val isLoadingCameraFiles: Boolean = false,
    val isScanningCameraFiles: Boolean = false,
    val isTransferringFiles: Boolean = false,
    val currentPath: String = "",
    val folders: List<RemoteFolder> = emptyList(),
    val selectedSourceTreeUri: String? = null,
    val cameraFiles: List<UsbSourceOrchestrator.CameraPickItem> = emptyList(),
    val selectedCameraFileIds: Set<String> = emptySet(),
    val currentTransferFileName: String? = null,
    val transferredFilesCount: Int = 0,
    val failedFilesCount: Int = 0,
    val totalFilesToTransfer: Int = 0,
    val transferProgressBytes: Long = 0L,
    val transferTotalBytes: Long = 0L,
    val errorMessage: String? = null,
    val folderSelected: Boolean = false,
    val isWritable: Boolean = true
) {
    val hasCameraFiles: Boolean
        get() = cameraFiles.isNotEmpty()

    val hasSelectedCameraFiles: Boolean
        get() = selectedCameraFileIds.isNotEmpty()

    val selectedCameraFilesCount: Int
        get() = selectedCameraFileIds.size

    val hasTransferInProgress: Boolean
        get() = isTransferringFiles

    val transferProgressPercent: Int
        get() = if (transferTotalBytes > 0L) {
            ((transferProgressBytes * 100L) / transferTotalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

    val hasTransferResult: Boolean
        get() = transferredFilesCount > 0 || failedFilesCount > 0
}