package com.rvmeur.transferin.domain.usecase

import com.rvmeur.transferin.domain.model.CameraMedia
import com.rvmeur.transferin.domain.repository.CameraMediaRepository

class ListCameraMediaUseCase(
    private val repository: CameraMediaRepository
) {
    suspend operator fun invoke(storageId: Int): List<CameraMedia> {
        return repository.listMedia(storageId)
    }
}