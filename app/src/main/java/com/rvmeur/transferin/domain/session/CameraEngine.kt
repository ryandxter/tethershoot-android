package com.rvmeur.transferin.domain.session

import com.rvmeur.transferin.domain.model.CameraRuntimeState
import com.rvmeur.transferin.domain.model.MediaJob
import com.rvmeur.transferin.domain.model.ProjectSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CameraEngine {
    val runtimeState: StateFlow<CameraRuntimeState>
    val incomingMedia: Flow<MediaJob>
    
    fun connect(projectSession: ProjectSession)
    fun disconnect()
    fun refresh()
}
