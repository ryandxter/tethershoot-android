package com.rvmeur.transferin.data.usb.fuzz

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/**
 * Adversarial USB Packet and PTP Container Fuzz Mutation Engine.
 * Generates deterministic pseudo-random malformed packets for stress-testing
 * camera tethering adapters, parsers, and transport managers.
 */
class UsbFuzzPayloadGenerator(seed: Long = 0xDEADBEEFCAFE) {

    private val random = Random(seed)

    companion object {
        const val PTP_USB_CONTAINER_COMMAND = 1
        const val PTP_USB_CONTAINER_DATA = 2
        const val PTP_USB_CONTAINER_RESPONSE = 3
        const val PTP_USB_CONTAINER_EVENT = 4

        val COMMON_MALFORMED_LENGTHS = intArrayOf(
            0, 1, 2, 3, 4, 5, 8, 11, 12, 13,
            Int.MAX_VALUE, Int.MIN_VALUE, -1, -12,
            0x7FFFFFFF, 0x00010000, 0x00FFFFFF, 500 * 1024 * 1024
        )

        val MUTATED_CONTAINER_TYPES = intArrayOf(
            0, 5, 6, 7, 0x00FF, 0xFFFF, -1, 0x1234
        )

        val VENDOR_RESPONSE_CODES = intArrayOf(
            0x2001, // OK
            0x2002, // General Error
            0x2003, // Session Not Open
            0x2005, // Operation Not Supported
            0x2008, // Device Busy
            0x2019, // PTP_RC_DeviceBusy (Nikon/Generic)
            0x201A, // Transaction Canceled
            0x201B, // Destination Unsupported
            0x0000,
            0xFFFF,
            0x9999
        )
    }

    /**
     * Generates a randomized malformed PTP container ByteArray.
     */
    fun generateMalformedPtpContainer(maxLength: Int = 1024): ByteArray {
        val strategy = random.nextInt(6)
        return when (strategy) {
            0 -> generateTruncatedHeader()
            1 -> generateInvalidLengthHeader()
            2 -> generateCorruptedTypeOrCode()
            3 -> generateZeroLengthPacket()
            4 -> generateRandomGarbage(random.nextInt(maxLength) + 1)
            else -> generateMalformedDataContainer()
        }
    }

    /**
     * Header with fewer than 12 bytes.
     */
    fun generateTruncatedHeader(): ByteArray {
        val len = random.nextInt(12)
        val bytes = ByteArray(len)
        random.nextBytes(bytes)
        return bytes
    }

    /**
     * Valid 12-byte header structure with mutated length declaration.
     */
    fun generateInvalidLengthHeader(): ByteArray {
        val declaredLen = COMMON_MALFORMED_LENGTHS[random.nextInt(COMMON_MALFORMED_LENGTHS.size)]
        val containerType = if (random.nextBoolean()) {
            PTP_USB_CONTAINER_RESPONSE
        } else {
            MUTATED_CONTAINER_TYPES[random.nextInt(MUTATED_CONTAINER_TYPES.size)]
        }
        val code = VENDOR_RESPONSE_CODES[random.nextInt(VENDOR_RESPONSE_CODES.size)]
        val txId = random.nextInt(1000)

        val actualPayloadSize = random.nextInt(64)
        val buffer = ByteBuffer.allocate(12 + actualPayloadSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(declaredLen)
        buffer.putShort(containerType.toShort())
        buffer.putShort(code.toShort())
        buffer.putInt(txId)
        val garbage = ByteArray(actualPayloadSize)
        random.nextBytes(garbage)
        buffer.put(garbage)
        return buffer.array()
    }

    /**
     * Container with mutated type or invalid response/event code.
     */
    fun generateCorruptedTypeOrCode(): ByteArray {
        val payloadLen = random.nextInt(128)
        val totalLen = 12 + payloadLen
        val buffer = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(totalLen)
        buffer.putShort(MUTATED_CONTAINER_TYPES[random.nextInt(MUTATED_CONTAINER_TYPES.size)].toShort())
        buffer.putShort(VENDOR_RESPONSE_CODES[random.nextInt(VENDOR_RESPONSE_CODES.size)].toShort())
        buffer.putInt(random.nextInt(5000))
        val payload = ByteArray(payloadLen)
        random.nextBytes(payload)
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Zero-length packet (ZLP) simulating empty USB transfer.
     */
    fun generateZeroLengthPacket(): ByteArray = ByteArray(0)

    /**
     * Pure random noise/garbage bytes.
     */
    fun generateRandomGarbage(length: Int): ByteArray {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }

    /**
     * Data container with declared length mismatch.
     */
    fun generateMalformedDataContainer(): ByteArray {
        val declaredLen = random.nextInt(1024) + 12
        val actualLen = random.nextInt(512) + 12
        val buffer = ByteBuffer.allocate(actualLen).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(declaredLen)
        buffer.putShort(PTP_USB_CONTAINER_DATA.toShort())
        buffer.putShort(random.nextInt(0xFFFF).toShort())
        buffer.putInt(random.nextInt(10000))
        if (actualLen > 12) {
            val payload = ByteArray(actualLen - 12)
            random.nextBytes(payload)
            buffer.put(payload)
        }
        return buffer.array()
    }

    /**
     * Generates a corrupted ISO-BMFF (.CR3) box stream.
     */
    fun generateMalformedIsoBmffBox(): ByteArray {
        val boxTypes = arrayOf("ftyp", "moov", "prvw", "thmb", "mdat", "uuid", "junk", "\u0000\u0000\u0000\u0000")
        val chosenType = boxTypes[random.nextInt(boxTypes.size)]
        val boxLen = COMMON_MALFORMED_LENGTHS[random.nextInt(COMMON_MALFORMED_LENGTHS.size)]
        val actualSize = random.nextInt(256) + 8
        val buffer = ByteBuffer.allocate(actualSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(boxLen)
        buffer.put(chosenType.toByteArray(Charsets.US_ASCII).copyOf(4))
        val data = ByteArray(actualSize - 8)
        random.nextBytes(data)
        buffer.put(data)
        return buffer.array()
    }

    /**
     * Generates a corrupted TIFF/SubIFD header stream (.ARW / .NEF).
     */
    fun generateMalformedTiffSubIfd(): ByteArray {
        val isLittleEndian = random.nextBoolean()
        val order = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val buffer = ByteBuffer.allocate(random.nextInt(256) + 16).order(order)
        if (isLittleEndian) {
            buffer.put('I'.code.toByte()).put('I'.code.toByte())
        } else {
            buffer.put('M'.code.toByte()).put('M'.code.toByte())
        }
        buffer.putShort(if (random.nextBoolean()) 42 else random.nextInt(0xFFFF).toShort())
        buffer.putInt(random.nextInt(10000) - 5000) // Mutated IFD0 offset
        val remaining = buffer.remaining()
        val garbage = ByteArray(remaining)
        random.nextBytes(garbage)
        buffer.put(garbage)
        return buffer.array()
    }
}
