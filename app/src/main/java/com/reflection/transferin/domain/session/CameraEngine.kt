package com.reflection.transferin.domain.session

import com.reflection.transferin.domain.model.CameraRuntimeState
import com.reflection.transferin.domain.model.MediaJob
import com.reflection.transferin.domain.model.ProjectSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CameraEngine {
    val runtimeState: StateFlow<CameraRuntimeState>
    val incomingMedia: Flow<MediaJob>
    
    fun connect(projectSession: ProjectSession)
    fun disconnect()
    fun refresh()
}
