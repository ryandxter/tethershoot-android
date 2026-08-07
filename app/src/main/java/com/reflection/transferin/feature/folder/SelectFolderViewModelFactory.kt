package com.reflection.transferin.feature.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.reflection.transferin.data.local.dao.SessionDao
import com.reflection.transferin.data.repository.SessionRepositoryImpl
import com.reflection.transferin.data.sftp.SftpClient
import com.reflection.transferin.data.usb.UsbSourceOrchestrator
import com.reflection.transferin.domain.repository.NasConnectionRepository
import com.reflection.transferin.domain.repository.SessionRepositoryAdapter
import com.reflection.transferin.domain.session.ProjectSessionManager
import com.reflection.transferin.domain.session.ProjectSessionManagerImpl

class SelectFolderViewModelFactory(
    private val nasConnectionRepository: NasConnectionRepository,
    private val sftpClient: SftpClient,
    private val sessionDao: SessionDao,
    private val usbSourceOrchestrator: UsbSourceOrchestrator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SelectFolderViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        val sessionRepositoryImpl = SessionRepositoryImpl(sessionDao)
        val sessionRepositoryAdapter = SessionRepositoryAdapter(sessionRepositoryImpl)
        val projectSessionManager: ProjectSessionManager =
            ProjectSessionManagerImpl(sessionRepositoryAdapter)

        return SelectFolderViewModel(
            nasConnectionRepository = nasConnectionRepository,
            sftpClient = sftpClient,
            projectSessionManager = projectSessionManager,
            usbSourceOrchestrator = usbSourceOrchestrator
        ) as T
    }
}
