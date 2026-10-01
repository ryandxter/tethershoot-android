package com.rvmeur.transferin.domain.model

data class CameraMedia(
    val storageId: Int = 0,
    val handle: Int,
    val name: String,
    val isFolder: Boolean = false,
    val size: Long = 0L,
    val parentHandle: Int = 0,
    val formatCode: Int = 0,
    val modifiedTime: Long? = null
)