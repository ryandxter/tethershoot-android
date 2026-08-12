package com.rvmeur.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl

/**
 * Factory for creating GalleryViewModel with dependencies.
 */
class GalleryViewModelFactory(
    private val application: Application,
    private val transferQueueRepository: TransferQueueRepositoryImpl
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(application, transferQueueRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
