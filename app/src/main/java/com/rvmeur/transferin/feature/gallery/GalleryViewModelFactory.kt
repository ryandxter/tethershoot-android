package com.rvmeur.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.domain.sync.SyncOrchestrator

/**
 * Factory for creating GalleryViewModel with dependencies.
 */
class GalleryViewModelFactory(
    private val application: Application,
    private val transferQueueRepository: TransferQueueRepositoryImpl? = null,
    private val cameraSyncDao: CameraSyncDao? = null,
    private val syncOrchestrator: SyncOrchestrator? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(
                application = application,
                transferQueueRepository = transferQueueRepository,
                cameraSyncDao = cameraSyncDao,
                syncOrchestrator = syncOrchestrator
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
