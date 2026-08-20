package com.rvmeur.transferin.domain.sync

import java.security.MessageDigest

/**
 * High-performance, deterministic SHA-256 Compound Media Fingerprint Generator.
 *
 * Constructs a unique digest across:
 * `"${vendorId}_${serialNumber}_${objectHandle}_${fileSizeBytes}_${captureTimestampMs}"`
 *
 * Guarantees idempotent deduplication across USB reconnects, SD card swaps, and app restarts.
 */
object CompoundFingerprintGenerator {

    private const val UNKNOWN_SERIAL = "UNKNOWN_SERIAL"

    /**
     * Generates a 64-character lowercase hexadecimal SHA-256 compound fingerprint.
     *
     * @param vendorId USB vendor ID (e.g. 0x04CB for Fuji, 0x04A9 for Canon, 0x054C for Sony, 0x04B0 for Nikon)
     * @param serialNumber Camera body serial number or fallback hardware identifier
     * @param objectHandle PTP / MTP object handle (or filesystem handle)
     * @param fileSizeBytes Exact file size in bytes
     * @param captureTimestampMs Capture epoch timestamp in milliseconds (normalized to 0 if negative)
     */
    fun generate(
        vendorId: Int,
        serialNumber: String?,
        objectHandle: Int,
        fileSizeBytes: Long,
        captureTimestampMs: Long
    ): String {
        val normalizedSerial = serialNumber?.trim()?.takeUnless { it.isEmpty() } ?: UNKNOWN_SERIAL
        val normalizedTimestamp = captureTimestampMs.coerceAtLeast(0L)
        val canonical = "${vendorId}_${normalizedSerial}_${objectHandle}_${fileSizeBytes}_${normalizedTimestamp}"

        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(digest.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Builds the canonical unhashed string for diagnostic and debugging purposes.
     */
    fun buildCanonicalString(
        vendorId: Int,
        serialNumber: String?,
        objectHandle: Int,
        fileSizeBytes: Long,
        captureTimestampMs: Long
    ): String {
        val normalizedSerial = serialNumber?.trim()?.takeUnless { it.isEmpty() } ?: UNKNOWN_SERIAL
        val normalizedTimestamp = captureTimestampMs.coerceAtLeast(0L)
        return "${vendorId}_${normalizedSerial}_${objectHandle}_${fileSizeBytes}_${normalizedTimestamp}"
    }
}
