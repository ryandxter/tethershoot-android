package com.rvmeur.transferin.feature.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvmeur.transferin.data.local.dao.SessionDao
import com.rvmeur.transferin.data.repository.SessionRepositoryImpl
import com.rvmeur.transferin.data.sftp.SftpClient
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.repository.NasConnectionRepository
import com.rvmeur.transferin.domain.repository.SessionRepositoryAdapter
import com.rvmeur.transferin.domain.session.ProjectSessionManager
import com.rvmeur.transferin.domain.session.ProjectSessionManagerImpl

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
