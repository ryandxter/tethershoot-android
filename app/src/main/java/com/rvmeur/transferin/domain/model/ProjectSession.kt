package com.rvmeur.transferin.domain.model

import com.rvmeur.transferin.domain.culling.CullingPolicy

data class ProjectSession(
    val id: String,
    val name: String,
    val targetNasPath: String,
    val sourceFolderPath: String,
    val connectionMode: ConnectionMode,
    val isActive: Boolean = false,
    val customerName: String? = null,
    val cullingPolicy: CullingPolicy = CullingPolicy.AUTO_SKIP_UPLOAD
)
