package com.rvmeur.transferin.domain.model

import java.util.Locale

enum class FileType(
    val priority: Int,
    val extensions: Set<String>
) {
    JPEG(
        priority = 30,
        extensions = setOf("jpg", "jpeg")
    ),
    RAW(
        priority = 10,
        extensions = setOf("cr2", "cr3", "nef", "arw", "raf", "dng", "rw2", "orf", "pef")
    ),
    OTHER(
        priority = 0,
        extensions = emptySet()
    );

    val isUploadable: Boolean
        get() = this == JPEG || this == RAW

    companion object {
        fun fromFileName(fileName: String?): FileType {
            if (fileName.isNullOrBlank()) return OTHER

            val extension = fileName
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)

            return entries.firstOrNull { type ->
                extension in type.extensions
            } ?: OTHER
        }

        fun fromMimeType(mimeType: String?): FileType {
            if (mimeType.isNullOrBlank()) return OTHER

            val normalized = mimeType.lowercase(Locale.ROOT)

            return when {
                normalized == "image/jpeg" -> JPEG
                normalized.startsWith("image/") && normalized.contains("raw") -> RAW
                else -> OTHER
            }
        }
    }
}