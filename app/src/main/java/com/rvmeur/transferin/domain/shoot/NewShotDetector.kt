package com.rvmeur.transferin.domain.shoot

import com.rvmeur.transferin.domain.model.CameraMedia

/**
 * Detector for identifying new shots by comparing current media against a baseline.
 *
 * ## Purpose
 * NewShotDetector identifies files that are new since the start of a session
 * by comparing the current CTG (Camera Transform Graph) state against a
 * previously captured baseline snapshot.
 *
 * ## Fingerprint Generation
 * Media items are converted to fingerprints using the same format as
 * [MediaFingerprint.toStorageKey]: `{handle}|{normalizedName}|{size}`
 * - handle: CTG storage handle
 * - normalizedName: lowercase, trimmed filename
 * - size: file size in bytes
 *
 * ## Usage Pattern
 * 1. Capture baseline: Call [extractFingerprints] on initial media list
 * 2. During session: Call [detectNewShots] with current media vs baseline
 * 3. The returned list contains only items NOT in the baseline (i.e., new shots)
 *
 * ## Integration
 * Works with [com.rvmeur.transferin.domain.model.CameraMedia] from CTG scans.
 * The baseline fingerprints can be passed to [SmartUploadFilter.initializeWith]
 * for session-scoped deduplication.
 *
 * @see SmartUploadFilter for higher-level upload filtering
 * @see MediaFingerprint for fingerprint format specification
 */
class NewShotDetector {

    /**
     * Detect new shots by comparing current media against baseline.
     *
     * Items are considered "new" if their fingerprint is NOT present in
     * the baseline set. This identifies files added since the baseline
     * was captured.
     *
     * @param baseline Set of fingerprint strings representing media state at baseline
     * @param current List of [CameraMedia] items representing current state
     * @return List of [CameraMedia] items that are NOT in the baseline (new shots)
     */
    fun detectNewShots(baseline: Set<String>, current: List<CameraMedia>): List<CameraMedia> {
        return current.filter { media ->
            buildFingerprint(media) !in baseline
        }
    }

    /**
     * Build a fingerprint string from a [CameraMedia] item.
     *
     * The fingerprint format is: `{handle}|{normalizedName}|{size}`
     *
     * @param media The media item to fingerprint
     * @return Fingerprint string in storage key format
     */
    fun buildFingerprint(media: CameraMedia): String {
        val normalized = media.name.trim().lowercase()
        return "${media.handle}|$normalized|${media.size}"
    }

    /**
     * Extract fingerprints from a list of media items.
     *
     * @param mediaList List of [CameraMedia] items
     * @return Set of fingerprint strings for all items
     */
    fun extractFingerprints(mediaList: List<CameraMedia>): Set<String> {
        return mediaList.mapTo(mutableSetOf()) { buildFingerprint(it) }
    }

    /**
     * Check if a single media item is a new shot relative to baseline.
     *
     * @param baseline Set of fingerprint strings representing the baseline
     * @param media The media item to check
     * @return true if media fingerprint is NOT in baseline (is new)
     */
    fun isNewShot(baseline: Set<String>, media: CameraMedia): Boolean {
        return buildFingerprint(media) !in baseline
    }

    /**
     * Parse a fingerprint string back to a [MediaFingerprint].
     *
     * @param fingerprint The fingerprint string to parse
     * @return [MediaFingerprint] if parsing succeeds, null otherwise
     */
    fun parseFingerprint(fingerprint: String): MediaFingerprint? {
        return MediaFingerprint.fromStorageKey(fingerprint)
    }

    /**
     * Validate that a fingerprint string is properly formatted.
     *
     * @param fingerprint The fingerprint string to validate
     * @return true if fingerprint can be parsed, false otherwise
     */
    fun isValidFingerprint(fingerprint: String): Boolean {
        return MediaFingerprint.fromStorageKey(fingerprint) != null
    }

    /**
     * Event representing a change in CTG state.
     *
     * This data class captures the essential information from a file change
     * event for processing and logging purposes.
     *
     * @property handle CTG storage handle of the file
     * @property fileName Original filename
     * @property fileSize File size in bytes
     * @property timestamp Time when the change was detected (epoch ms)
     */
    data class CtgChangeEvent(
        val handle: Int,
        val fileName: String,
        val fileSize: Long,
        val timestamp: Long
    )
}
