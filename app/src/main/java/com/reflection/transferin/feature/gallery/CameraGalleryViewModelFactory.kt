package com.reflection.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.reflection.transferin.data.local.dao.CameraMediaDao
import com.reflection.transferin.data.repository.CameraMediaRepositoryImpl
import com.reflection.transferin.data.usb.UsbHelper
import com.reflection.transferin.domain.repository.CameraMediaRepository

/**
 * Factory for CameraGalleryViewModel
 */
class CameraGalleryViewModelFactory(
    private val application: Application,
    private val cameraMediaDao: CameraMediaDao,
    private val usbHelper: UsbHelper,
    private val deviceId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraGalleryViewModel::class.java)) {
            val repository = CameraMediaRepositoryImpl(
                context = application,
                usbHelper = usbHelper,
                cameraMediaDao = cameraMediaDao
            )
            return CameraGalleryViewModel(
                application = application,
                cameraMediaRepository = repository,
                deviceId = deviceId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
