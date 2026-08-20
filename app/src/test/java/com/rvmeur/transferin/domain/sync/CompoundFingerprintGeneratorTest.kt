package com.rvmeur.transferin.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundFingerprintGeneratorTest {

    @Test
    fun `generate produces deterministic 64-character lowercase hex string`() {
        val fp1 = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "FUJI987654",
            objectHandle = 0x1001,
            fileSizeBytes = 52_428_800L,
            captureTimestampMs = 1715000000000L
        )

        val fp2 = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "FUJI987654",
            objectHandle = 0x1001,
            fileSizeBytes = 52_428_800L,
            captureTimestampMs = 1715000000000L
        )

        assertEquals(64, fp1.length)
        assertTrue(fp1.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(fp1, fp2)
    }

    @Test
    fun `varying any single parameter produces distinct fingerprint`() {
        val base = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_1",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val diffVendor = CompoundFingerprintGenerator.generate(
            vendorId = 0x04A9, // Canon
            serialNumber = "SERIAL_1",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val diffSerial = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_2",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val diffHandle = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_1",
            objectHandle = 101,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val diffSize = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_1",
            objectHandle = 100,
            fileSizeBytes = 1001L,
            captureTimestampMs = 5000L
        )

        val diffTime = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_1",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5001L
        )

        assertNotEquals(base, diffVendor)
        assertNotEquals(base, diffSerial)
        assertNotEquals(base, diffHandle)
        assertNotEquals(base, diffSize)
        assertNotEquals(base, diffTime)
    }

    @Test
    fun `serial number trimming and normalization`() {
        val fpUntrimmed = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "  SERIAL_XYZ  ",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val fpTrimmed = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL_XYZ",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        assertEquals(fpUntrimmed, fpTrimmed)

        val fpNull = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = null,
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        val fpEmpty = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 5000L
        )

        assertEquals(fpNull, fpEmpty)
    }

    @Test
    fun `negative timestamp normalized to zero`() {
        val fpNeg = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = -9999L
        )

        val fpZero = CompoundFingerprintGenerator.generate(
            vendorId = 0x04CB,
            serialNumber = "SERIAL",
            objectHandle = 100,
            fileSizeBytes = 1000L,
            captureTimestampMs = 0L
        )

        assertEquals(fpZero, fpNeg)
    }
}
