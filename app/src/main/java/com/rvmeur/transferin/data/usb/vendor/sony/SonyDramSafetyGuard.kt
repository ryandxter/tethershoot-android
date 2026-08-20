package com.rvmeur.transferin.data.usb.vendor.sony

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class SonyDramSafetyStatus {
    object Safe : SonyDramSafetyStatus()
    data class Remediated(val previousMode: Int, val currentMode: Int) : SonyDramSafetyStatus()
    data class Warning(val message: String, val currentSaveDestination: Int) : SonyDramSafetyStatus()
}

/**
 * Sony DRAM Safety Guard.
 *
 * Enforces automated validation and switching of the camera's Save Destination property
 * (0xD2C1 / 0xD2C2) to PC + Camera (0x0002).
 *
 * If the camera is configured to volatile PC Only (0x0001), images reside exclusively in
 * camera DRAM and are permanently lost if the USB connection drops or the app suspends.
 */
class SonyDramSafetyGuard {

    companion object {
        private const val TAG = "SonyDramSafetyGuard"
    }

    /**
     * Inspects save destination property and automatically remediates to PC + Camera if necessary.
     */
    fun verifyAndEnforceSafety(
        transport: SonyPtpTransport,
        transactionIdProvider: () -> Int
    ): SonyDramSafetyStatus {
        Log.d(TAG, "Checking Sony Save Destination property for DRAM safety...")

        val currentDest = readSaveDestination(transport, transactionIdProvider)
        if (currentDest == null) {
            Log.w(TAG, "Could not read Sony save destination property (camera may use default safe storage).")
            return SonyDramSafetyStatus.Safe
        }

        Log.d(TAG, "Current Sony Save Destination: 0x${currentDest.toString(16)} ($currentDest)")

        when (currentDest) {
            SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA,
            SonyPtpConstants.SAVE_DEST_CAMERA_ONLY -> {
                Log.d(TAG, "DRAM safety check passed: Save Destination is safe ($currentDest).")
                return SonyDramSafetyStatus.Safe
            }

            SonyPtpConstants.SAVE_DEST_PC_ONLY -> {
                Log.w(TAG, "CRITICAL: Sony camera is in volatile PC ONLY mode (0x0001). Images will be lost on disconnect! Attempting auto-remediation...")
                val remediationSuccess = attemptRemediation(
                    transport = transport,
                    targetMode = SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA,
                    transactionIdProvider = transactionIdProvider
                )

                if (remediationSuccess) {
                    val verified = readSaveDestination(transport, transactionIdProvider)
                    if (verified == SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA) {
                        Log.i(TAG, "Successfully remediated Sony Save Destination to PC + Camera (0x0002).")
                        return SonyDramSafetyStatus.Remediated(
                            previousMode = SonyPtpConstants.SAVE_DEST_PC_ONLY,
                            currentMode = SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA
                        )
                    }
                }

                val warningMsg = "Camera is configured to PC Only mode (0x0001). Images will only exist in volatile camera DRAM and cannot be preserved if disconnected."
                Log.e(TAG, warningMsg)
                return SonyDramSafetyStatus.Warning(
                    message = warningMsg,
                    currentSaveDestination = currentDest
                )
            }

            else -> {
                Log.w(TAG, "Unknown Sony save destination value: $currentDest. Assuming safe.")
                return SonyDramSafetyStatus.Safe
            }
        }
    }

    private fun readSaveDestination(
        transport: SonyPtpTransport,
        transactionIdProvider: () -> Int
    ): Int? {
        // Try reading 0xD2C1 first, then 0xD2C2
        val properties = listOf(SonyPtpConstants.DPC_SAVE_MEDIA, SonyPtpConstants.DPC_SAVE_DESTINATION_ALT)

        for (propCode in properties) {
            // First try Sony vendor op 0x9802
            val vendorPayload = tryGetPropValue(
                transport = transport,
                opCode = SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE,
                propCode = propCode,
                txId = transactionIdProvider()
            )
            if (vendorPayload != null) {
                val value = parsePropValue(vendorPayload)
                if (value != null) return value
            }

            // Fallback to standard PTP op 0x1015
            val stdPayload = tryGetPropValue(
                transport = transport,
                opCode = SonyPtpConstants.OC_GET_DEVICE_PROP_VALUE,
                propCode = propCode,
                txId = transactionIdProvider()
            )
            if (stdPayload != null) {
                val value = parsePropValue(stdPayload)
                if (value != null) return value
            }
        }

        return null
    }

    private fun tryGetPropValue(
        transport: SonyPtpTransport,
        opCode: Int,
        propCode: Int,
        txId: Int
    ): ByteArray? {
        return runCatching {
            val response = transport.transact(
                operationCode = opCode,
                transactionId = txId,
                params = intArrayOf(propCode)
            )
            if (response.responseCode == SonyPtpConstants.RC_OK) {
                response.dataPayload
            } else null
        }.getOrNull()
    }

    private fun parsePropValue(payload: ByteArray): Int? {
        if (payload.isEmpty()) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return when {
            payload.size >= 4 -> bb.int
            payload.size >= 2 -> bb.short.toInt() and 0xFFFF
            else -> bb.get().toInt() and 0xFF
        }
    }

    private fun attemptRemediation(
        transport: SonyPtpTransport,
        targetMode: Int,
        transactionIdProvider: () -> Int
    ): Boolean {
        val properties = listOf(SonyPtpConstants.DPC_SAVE_MEDIA, SonyPtpConstants.DPC_SAVE_DESTINATION_ALT)

        // 2-byte little endian payload for targetMode
        val payload = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(targetMode.toShort()).array()

        for (propCode in properties) {
            // Try Sony vendor op 0x9803
            val vendorSuccess = runCatching {
                val response = transport.transactWithData(
                    operationCode = SonyPtpConstants.OC_SONY_SET_DEVICE_PROP_VALUE,
                    transactionId = transactionIdProvider(),
                    params = intArrayOf(propCode),
                    data = payload
                )
                response.responseCode == SonyPtpConstants.RC_OK
            }.getOrDefault(false)

            if (vendorSuccess) return true

            // Try standard PTP op 0x1016
            val stdSuccess = runCatching {
                val response = transport.transactWithData(
                    operationCode = SonyPtpConstants.OC_SET_DEVICE_PROP_VALUE,
                    transactionId = transactionIdProvider(),
                    params = intArrayOf(propCode),
                    data = payload
                )
                response.responseCode == SonyPtpConstants.RC_OK
            }.getOrDefault(false)

            if (stdSuccess) return true
        }

        return false
    }
}
