package com.reflection.transferin.domain.model

enum class TransferItemStatus {
    DETECTED,
    WAITING_STABLE,
    QUEUED,
    DISPATCHED,
    UPLOADING,
    SUCCESS,
    FAILED,
    RETRY_SCHEDULED,
    SKIPPED_DUPLICATE,
    CANCELLED;

    val isPending: Boolean
        get() = this == DETECTED ||
                this == WAITING_STABLE ||
                this == QUEUED ||
                this == DISPATCHED ||
                this == RETRY_SCHEDULED

    val isInFlight: Boolean
        get() = this == DISPATCHED || this == UPLOADING

    val isFinished: Boolean
        get() = this == SUCCESS ||
                this == FAILED ||
                this == SKIPPED_DUPLICATE ||
                this == CANCELLED

    val isSuccessfulEndState: Boolean
        get() = this == SUCCESS || this == SKIPPED_DUPLICATE
}