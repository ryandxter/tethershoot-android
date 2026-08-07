package com.reflection.transferin.feature.sessionactive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.reflection.transferin.data.local.dao.SessionDao
import com.reflection.transferin.data.local.db.DatabaseProvider
import com.reflection.transferin.data.repository.SessionRepositoryImpl
import com.reflection.transferin.data.repository.TransferQueueRepositoryImpl
import com.reflection.transferin.data.usb.DocumentFileScanner
import com.reflection.transferin.data.usb.FileStabilityChecker
import com.reflection.transferin.data.usb.UsbFileInfo
import com.reflection.transferin.data.usb.UsbFileInfoProvider
import com.reflection.transferin.data.usb.UsbHelper
import com.reflection.transferin.data.usb.UsbSourceOrchestrator
import com.reflection.transferin.domain.repository.SessionRepositoryAdapter
import com.reflection.transferin.domain.session.ProjectSessionManager
import com.reflection.transferin.domain.session.ProjectSessionManagerImpl
import com.reflection.transferin.domain.session.SourceWatcher
import com.reflection.transferin.domain.session.UploadQueueManager

class SessionViewModelFactory(
    private val app: Application,
    private val usbHelper: UsbHelper? = null,
    private val usbSourceOrchestrator: UsbSourceOrchestrator? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            val appContext = app.applicationContext
            val db = DatabaseProvider.getDatabase(appContext)

            val transferQueueDao = db.transferQueueDao()
            val sessionDao: SessionDao = db.sessionDao()

            val sessionRepositoryImpl = SessionRepositoryImpl(sessionDao)
            val transferQueueRepository = TransferQueueRepositoryImpl(transferQueueDao)

            val sessionRepositoryAdapter = SessionRepositoryAdapter(sessionRepositoryImpl)
            val projectSessionManager: ProjectSessionManager =
                ProjectSessionManagerImpl(sessionRepositoryAdapter)

            val uploadQueueManager = UploadQueueManager(
                context = appContext,
                transferQueueRepository = transferQueueRepository
            )

            // Create USB dependencies for ShootModeEngine
            val resolvedUsbHelper = usbHelper ?: UsbHelper(appContext)
            val documentFileScanner = DocumentFileScanner(appContext)

            // Create UsbFileInfoProvider for FileStabilityChecker
            val usbFileInfoProvider = object : UsbFileInfoProvider {
                override suspend fun getFileInfo(uri: Uri): UsbFileInfo? {
                    return runCatching {
                        val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(appContext, uri)
                            ?: return null

                        val sizeBytes = doc.length()
                        val lastModifiedEpochMs = doc.lastModified()

                        UsbFileInfo(
                            sizeBytes = sizeBytes.coerceAtLeast(0L),
                            lastModifiedEpochMs = lastModifiedEpochMs.coerceAtLeast(0L)
                        )
                    }.getOrNull()
                }
            }

            val fileStabilityChecker = FileStabilityChecker(usbFileInfoProvider)
            val sourceWatcher = SourceWatcher()
            val resolvedOrchestrator = usbSourceOrchestrator ?: UsbSourceOrchestrator(
                usbHelper = resolvedUsbHelper,
                documentFileScanner = documentFileScanner,
                fileStabilityChecker = fileStabilityChecker,
                sourceWatcher = sourceWatcher
            )

            return SessionViewModel(
                application = app,
                sessionRepository = sessionRepositoryImpl,
                transferQueueRepository = transferQueueRepository,
                projectSessionManager = projectSessionManager,
                uploadQueueManager = uploadQueueManager,
                usbHelper = resolvedUsbHelper,
                usbSourceOrchestrator = resolvedOrchestrator
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}