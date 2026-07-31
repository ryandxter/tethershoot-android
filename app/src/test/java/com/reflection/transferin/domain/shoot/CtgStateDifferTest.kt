package com.reflection.transferin.domain.shoot

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CtgStateDiffer.
 *
 * Tests all comparison scenarios: added, deleted, modified, unchanged.
 */
class CtgStateDifferTest {

    private val differ = CtgStateDiffer()

    @Test
    fun `diff detects added files`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "existing.jpg", 1024)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "existing.jpg", 1024),
            createEntry(2, "new.jpg", 2048)
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(1, result.added.size)
        assertEquals("new.jpg", result.added[0].filename)
        assertEquals(0, result.modified.size)
        assertEquals(0, result.deleted.size)
    }

    @Test
    fun `diff detects deleted files`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "keep.jpg", 1024),
            createEntry(2, "delete.jpg", 2048)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "keep.jpg", 1024)
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(0, result.added.size)
        assertEquals(0, result.modified.size)
        assertEquals(1, result.deleted.size)
        assertEquals("delete.jpg", result.deleted[0].filename)
    }

    @Test
    fun `diff detects modified files`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 1024, modifiedTime = 1000L)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 2048, modifiedTime = 2000L)  // Size and time changed
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(0, result.added.size)
        assertEquals(1, result.modified.size)
        assertEquals(0, result.deleted.size)
        assertEquals(2048L, result.modified[0].current.size)
        assertTrue(result.modified[0].reason.contains("Size"))
    }

    @Test
    fun `diff detects size-only modification`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 1024, modifiedTime = 1000L)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 2048, modifiedTime = 1000L)  // Only size changed
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(1, result.modified.size)
        assertTrue(result.modified[0].reason.contains("Size"))
    }

    @Test
    fun `diff detects time-only modification`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 1024, modifiedTime = 1000L)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "photo.jpg", 1024, modifiedTime = 2000L)  // Only time changed
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(1, result.modified.size)
        assertTrue(result.modified[0].reason.contains("Modified"))
    }

    @Test
    fun `diff handles empty snapshots`() {
        val baseline = CtgMediaSnapshot.createEmpty(1, "camera-1")
        val current = CtgMediaSnapshot.createEmpty(1, "camera-1")

        val result = differ.diff(baseline, current)

        assertFalse(result.hasChanges)
        assertEquals(0, result.changeCount)
        assertEquals(0, result.unchangedCount)
    }

    @Test
    fun `diff counts unchanged correctly`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),
            createEntry(2, "b.jpg", 1024),
            createEntry(3, "c.jpg", 1024)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),  // unchanged
            createEntry(2, "b.jpg", 1024),  // unchanged
            createEntry(3, "c.jpg", 1024),  // unchanged
            createEntry(4, "d.jpg", 1024)   // added
        ))

        val result = differ.diff(baseline, current)

        assertEquals(3, result.unchangedCount)
        assertEquals(1, result.added.size)
    }

    @Test
    fun `summary formats correctly with all changes`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),
            createEntry(2, "b.jpg", 1024)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),
            createEntry(3, "c.jpg", 1024),  // added
            createEntry(4, "d.jpg", 1024)   // added
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.summary.contains("+2"))
        assertTrue(result.summary.contains("-1"))  // b.jpg deleted
    }

    @Test
    fun `summary formats correctly with no changes`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024)
        ))

        val current = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024)
        ))

        val result = differ.diff(baseline, current)

        assertEquals("", result.summary)
    }

    @Test
    fun `diff captures baseline and current IDs`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024)
        ), id = "baseline-123")

        val current = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),
            createEntry(2, "b.jpg", 1024)
        ), id = "current-456")

        val result = differ.diff(baseline, current)

        assertEquals("baseline-123", result.baselineId)
        assertEquals("current-456", result.currentId)
    }

    @Test
    fun `diff handles completely different sets`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024),
            createEntry(2, "b.jpg", 2048)
        ))

        val current = createSnapshot(listOf(
            createEntry(3, "c.jpg", 3072),
            createEntry(4, "d.jpg", 4096)
        ))

        val result = differ.diff(baseline, current)

        assertTrue(result.hasChanges)
        assertEquals(2, result.added.size)
        assertEquals(2, result.deleted.size)
        assertEquals(0, result.modified.size)
        assertEquals(0, result.unchangedCount)
    }

    @Test
    fun `diff result computedAt is set`() {
        val baseline = createSnapshot(listOf(
            createEntry(1, "a.jpg", 1024)
        ))

        val beforeTest = System.currentTimeMillis()
        val result = differ.diff(baseline, baseline)
        val afterTest = System.currentTimeMillis()

        assertTrue(result.computedAt >= beforeTest)
        assertTrue(result.computedAt <= afterTest)
    }

    @Test
    fun `modified entry captures both baseline and current`() {
        val baselineEntry = createEntry(1, "photo.jpg", 1024, modifiedTime = 1000L)
        val currentEntry = createEntry(1, "photo.jpg", 2048, modifiedTime = 2000L)

        val baseline = createSnapshot(listOf(baselineEntry))
        val current = createSnapshot(listOf(currentEntry))

        val result = differ.diff(baseline, current)

        assertEquals(1, result.modified.size)
        val modified = result.modified[0]
        assertEquals(baselineEntry, modified.baseline)
        assertEquals(currentEntry, modified.current)
    }

    // Helper functions

    private fun createEntry(
        handle: Int,
        filename: String,
        size: Long,
        modifiedTime: Long = System.currentTimeMillis()
    ): CtgMediaEntry {
        return CtgMediaEntry(
            handle = handle,
            filename = filename,
            size = size,
            modifiedTime = modifiedTime,
            capturedTime = null,
            formatCode = 0x3001  // JPEG
        )
    }

    private fun createSnapshot(entries: List<CtgMediaEntry>, id: String = "test-${System.nanoTime()}"): CtgMediaSnapshot {
        val map = entries.associate { it.fingerprint to it }
        return CtgMediaSnapshot(
            id = id,
            capturedAt = System.currentTimeMillis(),
            storageId = 1,
            cameraId = "test-camera",
            mediaCount = entries.size,
            entries = map,
            checksum = "test-checksum"
        )
    }
}
