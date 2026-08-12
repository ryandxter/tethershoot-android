package com.rvmeur.transferin.domain.model

enum class SessionState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING_INTAKE,
    UPLOAD_DRAINING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == STARTING || this == RUNNING || this == STOPPING_INTAKE || this == UPLOAD_DRAINING

    val acceptsNewFiles: Boolean
        get() = this == STARTING || this == RUNNING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}