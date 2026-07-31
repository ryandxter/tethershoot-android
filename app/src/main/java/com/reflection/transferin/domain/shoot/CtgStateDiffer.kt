package com.reflection.transferin.domain.shoot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of comparing two snapshots.
 *
 * Captures all differences between a baseline and current snapshot,
 * including added, modified, and deleted files.
 *
 * @property baselineId ID of baseline snapshot
 * @property currentId ID of current snapshot
 * @property added Files present in current but not in baseline (new files)
 * @property modified Files that changed (size or timestamp) between snapshots
 * @property deleted Files present in baseline but not in current (deleted/removed)
 * @property unchangedCount Number of files that did not change
 * @property computedAt Timestamp when comparison was performed
 */
data class DiffResult(
    val baselineId: String,
    val currentId: String,
    val added: List<CtgMediaEntry>,
    val modified: List<ModifiedEntry>,
    val deleted: List<CtgMediaEntry>,
    val unchangedCount: Int,
    val computedAt: Long
) {
    /**
     * True if any changes were detected.
     */
    val hasChanges: Boolean
        get() = added.isNotEmpty() || modified.isNotEmpty() || deleted.isNotEmpty()

    /**
     * Total number of changed files.
     */
    val changeCount: Int
        get() = added.size + modified.size + deleted.size

    /**
     * Human-readable summary of changes.
     * Format: "+N" for added, "~N" for modified, "-N" for deleted
     */
    val summary: String
        get() = buildString {
            if (added.isNotEmpty()) append("+${added.size} ")
            if (modified.isNotEmpty()) append("~${modified.size} ")
            if (deleted.isNotEmpty()) append("-${deleted.size}")
        }.trim()
}

/**
 * Tracks a file that was modified between snapshots.
 *
 * Stores both the baseline and current state to enable detailed
 * reporting of what changed.
 *
 * @property baseline The entry state at baseline snapshot
 * @property current The entry state at current snapshot
 */
data class ModifiedEntry(
    val baseline: CtgMediaEntry,
    val current: CtgMediaEntry
) {
    /**
     * Human-readable description of what changed.
     * Example: "Size: 1.0 MB -> 2.0 MB; Modified: 2024-01-01 10:00:00 -> 2024-01-01 10:05:00"
     */
    val reason: String
        get() = buildString {
            if (baseline.size != current.size) {
                append("Size: ${baseline.displaySize} -> ${current.displaySize}")
            }
            if (baseline.modifiedTime != current.modifiedTime) {
                if (isNotEmpty()) append("; ")
                append("Modified: ${formatTime(baseline.modifiedTime)} -> ${formatTime(current.modifiedTime)}")
            }
        }.trim()
}

/**
 * Compares CTG snapshots to detect media changes.
 *
 * Uses Kotlin set operations for O(n) complexity instead of nested loops.
 * Immutable - returns new DiffResult on each comparison.
 * Thread-safe - all state is captured in DiffResult.
 *
 * Example usage:
 * ```
 * val differ = CtgStateDiffer()
 * val result = differ.diff(baselineSnapshot, currentSnapshot)
 *
 * if (result.hasChanges) {
 *     result.added.forEach { upload(it) }
 *     result.modified.forEach { reupload(it.current) }
 * }
 * ```
 */
class CtgStateDiffer {

    /**
     * Compare baseline snapshot against current snapshot.
     *
     * Uses set difference operations for efficient comparison:
     * - added = current - baseline (present now, not present before)
     * - deleted = baseline - current (present before, not present now)
     * - common = baseline intersect current
     * - modified = common entries where size or timestamp changed
     *
     * @param baseline The earlier snapshot to compare from
     * @param current The later snapshot to compare to
     * @return DiffResult describing all changes
     */
    fun diff(baseline: CtgMediaSnapshot, current: CtgMediaSnapshot): DiffResult {
        val baselineFingerprints = baseline.entries.keys
        val currentFingerprints = current.entries.keys

        // Set difference operations for O(n) complexity
        val addedFps = currentFingerprints - baselineFingerprints
        val deletedFps = baselineFingerprints - currentFingerprints
        val commonFps = baselineFingerprints intersect currentFingerprints

        // Check for modifications in common set
        val modifiedFps = commonFps.filter { fp ->
            val baselineEntry = baseline.entries[fp]!!
            val currentEntry = current.entries[fp]!!
            isModified(baselineEntry, currentEntry)
        }.toSet()

        val unchangedCount = commonFps.size - modifiedFps.size

        return DiffResult(
            baselineId = baseline.id,
            currentId = current.id,
            added = addedFps.map { current.entries[it]!! },
            modified = modifiedFps.map { fp ->
                ModifiedEntry(
                    baseline = baseline.entries[fp]!!,
                    current = current.entries[fp]!!
                )
            },
            deleted = deletedFps.map { baseline.entries[it]!! },
            unchangedCount = unchangedCount,
            computedAt = System.currentTimeMillis()
        )
    }

    /**
     * Detect if an entry has been modified (size or timestamp changed).
     *
     * @param baseline The baseline entry state
     * @param current The current entry state
     * @return true if size or modifiedTime differs
     */
    private fun isModified(baseline: CtgMediaEntry, current: CtgMediaEntry): Boolean {
        return baseline.size != current.size ||
               baseline.modifiedTime != current.modifiedTime
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
