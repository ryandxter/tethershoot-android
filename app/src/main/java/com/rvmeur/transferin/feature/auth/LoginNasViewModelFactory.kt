package com.rvmeur.transferin.feature.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvmeur.transferin.data.local.db.DatabaseProvider
import com.rvmeur.transferin.data.repository.NasConnectionRepositoryImpl
import com.rvmeur.transferin.data.sftp.DelegatingSftpClient

class LoginNasViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginNasViewModel::class.java)) {
            val db = DatabaseProvider.getDatabase(application)

            val repository = NasConnectionRepositoryImpl(
                dao = db.nasConnectionDao()
            )

            val sftpClient = DelegatingSftpClient(application)

            return LoginNasViewModel(
                nasRepo = repository,
                sftpClient = sftpClient
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}