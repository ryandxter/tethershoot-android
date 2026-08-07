package com.reflection.transferin.feature.sessionsummary

data class SummaryState(
    val totalFiles: Int = 0,
    val totalSuccess: Int = 0,
    val totalFailed: Int = 0,
    val startTime: String = "",
    val endTime: String = ""
)