package com.example.somnus.domain.assistant

import java.security.MessageDigest
import java.time.LocalDate

/**
 * High-performance, memory-bounded deterministic response cache with explicit TTL and data-change invalidation.
 *
 * Guarantees:
 * 1. Safe reuse: Cache key couples user query with exact date range and data fingerprint.
 * 2. Bounded memory: Evicts expired entries and enforces maximum size (100 items).
 * 3. Reactive invalidation: Cleared whenever new sleep, step, or food records are added or modified.
 * 4. Diagnostics tracking: Measures exact hits and misses for token efficiency metrics.
 */
class AssistantCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxCapacity: Int = 100
) {

    companion object {
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private data class CacheEntry(
        val response: String,
        val dataReferences: List<DataReference>,
        val createdAtMs: Long,
        val domainTag: DataReferenceDomain?
    )

    private val storage = mutableMapOf<String, CacheEntry>()

    var hitCount: Int = 0
        private set
    var missCount: Int = 0
        private set

    /**
     * Builds a deterministic cache key.
     */
    fun createKey(
        query: String,
        targetDate: LocalDate,
        dataFingerprint: String
    ): String {
        val raw = "${query.trim().lowercase()}::$targetDate::$dataFingerprint"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Retrieves an entry if present and within its TTL.
     */
    @Synchronized
    fun get(key: String, nowMs: Long = System.currentTimeMillis()): CacheEntryResult? {
        val entry = storage[key] ?: run {
            missCount++
            return null
        }

        if (nowMs - entry.createdAtMs > ttlMs) {
            storage.remove(key)
            missCount++
            return null
        }

        hitCount++
        return CacheEntryResult(entry.response, entry.dataReferences)
    }

    /**
     * Stores a response in the cache.
     */
    @Synchronized
    fun put(
        key: String,
        response: String,
        dataReferences: List<DataReference>,
        domainTag: DataReferenceDomain? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (storage.size >= maxCapacity) {
            // Evict oldest entry
            val oldest = storage.minByOrNull { it.value.createdAtMs }?.key
            if (oldest != null) storage.remove(oldest)
        }

        storage[key] = CacheEntry(
            response = response,
            dataReferences = dataReferences,
            createdAtMs = nowMs,
            domainTag = domainTag
        )
    }

    /**
     * Invalidates all cached summaries for a specific health domain when records update.
     */
    @Synchronized
    fun invalidateDomain(domain: DataReferenceDomain) {
        val keysToRemove = storage.filter { it.value.domainTag == domain }.keys
        keysToRemove.forEach { storage.remove(it) }
    }

    /**
     * Clears all cached entries.
     */
    @Synchronized
    fun clear() {
        storage.clear()
        hitCount = 0
        missCount = 0
    }

    @Synchronized
    fun size(): Int = storage.size
}

data class CacheEntryResult(
    val response: String,
    val dataReferences: List<DataReference>
)
