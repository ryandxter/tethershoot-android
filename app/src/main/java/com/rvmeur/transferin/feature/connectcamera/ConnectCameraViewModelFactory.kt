package com.rvmeur.transferin.feature.connectcamera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvmeur.transferin.data.local.dao.CameraMediaDao
import com.rvmeur.transferin.data.local.dao.SessionDao
import com.rvmeur.transferin.data.repository.CameraMediaRepositoryImpl
import com.rvmeur.transferin.data.repository.SessionRepositoryImpl
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.domain.repository.SessionRepositoryAdapter
import com.rvmeur.transferin.domain.session.ProjectSessionManager
import com.rvmeur.transferin.domain.session.ProjectSessionManagerImpl

class ConnectCameraViewModelFactory(
    private val context: Context,
    private val usbHelper: UsbHelper,
    private val sessionDao: SessionDao,
    private val cameraMediaDao: CameraMediaDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectCameraViewModel::class.java)) {
            val sessionRepository = SessionRepositoryImpl(sessionDao)
            val sessionRepositoryAdapter = SessionRepositoryAdapter(sessionRepository)
            val projectSessionManager: ProjectSessionManager =
                ProjectSessionManagerImpl(sessionRepositoryAdapter)

            val cameraMediaRepository = CameraMediaRepositoryImpl(
                context = context,
                usbHelper = usbHelper,
                cameraMediaDao = cameraMediaDao
            )

            return ConnectCameraViewModel(
                usbHelper = usbHelper,
                cameraMediaRepository = cameraMediaRepository,
                projectSessionManager = projectSessionManager
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}