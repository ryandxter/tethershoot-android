package com.reflection.transferin.domain.shoot

import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
import com.reflection.transferin.domain.repository.UploadedFingerprintRepository

/**
 * In-memory deduplication filter for upload operations.
 *
 * ## Purpose
 * SmartUploadFilter provides efficient in-memory fingerprint caching to avoid
 * repeated database lookups during upload processing. It maintains a Set of
 * known fingerprints that can be checked in O(1) time.
 *
 * ## Lazy Initialization
 * The filter uses lazy initialization to defer database loading until needed.
 * Call [initialize] or [initializeWith] before using [filter] or [isNew].
 *
 * ## Session Scoping
 * When initialized with a sessionId, the filter only loads fingerprints from
 * that session. When initialized without sessionId, it loads all fingerprints
 * for cross-session deduplication.
 *
 * ## Thread Safety
 * This class is not thread-safe. Access from a single coroutine context or
 * use synchronization for multi-threaded access.
 *
 * @param fingerprintRepository Repository for persisting fingerprints
 * @see NewShotDetector for baseline comparison during session
 * @see MediaFingerprint for fingerprint format details
 */
class SmartUploadFilter(
    private val fingerprintRepository: UploadedFingerprintRepository
) {

    private var uploadedFingerprints: Set<String> = emptySet()
    private var isInitialized: Boolean = false

    /**
     * Initialize filter by loading fingerprints from repository.
     *
     * @param sessionId Optional session ID to scope fingerprints to a session.
     *                 If null, loads all fingerprints for cross-session dedup.
     */
    suspend fun initialize(sessionId: String? = null) {
        uploadedFingerprints = if (sessionId != null) {
            fingerprintRepository.getSessionFingerprints(sessionId)
        } else {
            fingerprintRepository.getAllFingerprints()
        }
        isInitialized = true
    }

    /**
     * Initialize filter directly with a set of fingerprints.
     *
     * This bypasses repository access, useful for:
     * - Setting baseline from [NewShotDetector]
     * - Testing scenarios
     * - Pre-loading from alternative sources
     *
     * @param fingerprints Set of fingerprint strings to treat as already uploaded
     */
    fun initializeWith(fingerprints: Set<String>) {
        uploadedFingerprints = fingerprints
        isInitialized = true
    }

    /**
     * Filter a list of items, separating new items from already-uploaded items.
     *
     * @param items List of items implementing [FingerprintSource]
     * @return [FilterResult] containing new items, skipped items, and count
     * @throws IllegalStateException if filter not initialized
     */
    fun filter(items: List<FingerprintSource>): FilterResult {
        require(isInitialized) { "Filter not initialized. Call initialize() or initializeWith() first." }

        val newItems = mutableListOf<FingerprintSource>()
        val skippedItems = mutableListOf<FingerprintSource>()

        for (item in items) {
            if (item.fingerprint in uploadedFingerprints) {
                skippedItems.add(item)
            } else {
                newItems.add(item)
            }
        }

        return FilterResult(
            newItems = newItems,
            skippedItems = skippedItems,
            skippedCount = skippedItems.size
        )
    }

    /**
     * Check if a fingerprint represents a new (not yet uploaded) file.
     *
     * @param fingerprint The fingerprint string to check
     * @return true if fingerprint is NOT in the uploaded set (i.e., is new)
     * @throws IllegalStateException if filter not initialized
     */
    fun isNew(fingerprint: String): Boolean {
        require(isInitialized) { "Filter not initialized. Call initialize() or initializeWith() first." }
        return fingerprint !in uploadedFingerprints
    }

    /**
     * Record a successful upload to both memory and persistent storage.
     *
     * @param entity The upload fingerprint entity to record
     */
    suspend fun recordUpload(entity: UploadedFingerprintEntity) {
        // Add to in-memory cache
        addToCache(entity.fingerprint)
        // Persist to database
        fingerprintRepository.recordUpload(entity)
    }

    /**
     * Add a fingerprint to the in-memory cache without persisting.
     *
     * Use this when you want to temporarily track a fingerprint without
     * triggering a database write (e.g., during session baseline setup).
     *
     * @param fingerprint The fingerprint string to add
     */
    fun addToCache(fingerprint: String) {
        uploadedFingerprints = uploadedFingerprints + fingerprint
    }

    /**
     * Refresh the filter by reloading from repository.
     *
     * @param sessionId Optional session ID to scope fingerprints
     */
    suspend fun refresh(sessionId: String? = null) {
        isInitialized = false
        initialize(sessionId)
    }

    /**
     * Result of filtering items by upload status.
     *
     * @property newItems Items whose fingerprints are NOT in the uploaded set
     * @property skippedItems Items whose fingerprints ARE in the uploaded set
     * @property skippedCount Number of items that were skipped
     */
    data class FilterResult(
        val newItems: List<FingerprintSource>,
        val skippedItems: List<FingerprintSource>,
        val skippedCount: Int
    )

    /**
     * Interface for items that can provide a fingerprint for deduplication.
     *
     * Implement this interface on entities or DTOs that need to be filtered
     * by the [SmartUploadFilter].
     */
    interface FingerprintSource {
        val fingerprint: String
    }
}
