package com.reflection.transferin.domain.model

enum class WatcherStatus {
    IDLE,
    STARTING,
    SCANNING,
    WAITING_STABLE,
    ENQUEUEING,
    PAUSED,
    STOPPED,
    ERROR;

    val isRunning: Boolean
        get() = this == STARTING ||
                this == SCANNING ||
                this == WAITING_STABLE ||
                this == ENQUEUEING

    val canDetectNewFiles: Boolean
        get() = this == SCANNING || this == WAITING_STABLE

    val isTerminal: Boolean
        get() = this == STOPPED || this == ERROR
}