package com.reflection.transferin.domain.model

data class CameraMedia(
    val storageId: Int,
    val handle: Int,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val parentHandle: Int,
    val formatCode: Int,
    val modifiedTime: Long? = null
)