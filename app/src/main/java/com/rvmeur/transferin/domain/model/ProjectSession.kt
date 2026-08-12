package com.rvmeur.transferin.domain.model

data class ProjectSession(
    val id: String,
    val name: String,
    val targetNasPath: String,
    val sourceFolderPath: String,
    val connectionMode: ConnectionMode,
    val isActive: Boolean = false,
    val customerName: String? = null
)
