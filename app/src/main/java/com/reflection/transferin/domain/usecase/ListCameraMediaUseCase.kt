package com.reflection.transferin.domain.usecase

import com.reflection.transferin.domain.model.CameraMedia
import com.reflection.transferin.domain.repository.CameraMediaRepository

class ListCameraMediaUseCase(
    private val repository: CameraMediaRepository
) {
    suspend operator fun invoke(storageId: Int): List<CameraMedia> {
        return repository.listMedia(storageId)
    }
}