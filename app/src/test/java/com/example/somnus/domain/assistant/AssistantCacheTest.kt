package com.example.somnus.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AssistantCacheTest {

    private val cache = AssistantCache(ttlMs = 300_000L)
    private val today = LocalDate.of(2026, 10, 14)

    private val sampleReferences = listOf(
        DataReference(
            domain = DataReferenceDomain.SLEEP,
            label = "Sleep Duration",
            value = "7h 30m"
        )
    )

    @Test
    fun get_returnsNullOnCacheMiss() {
        val key = cache.createKey("How did I sleep?", today, "fp1")
        val cached = cache.get(key)
        assertNull(cached)

        assertEquals(1, cache.missCount)
        assertEquals(0, cache.hitCount)
    }

    @Test
    fun putAndGet_returnsCachedMessageOnHit() {
        val key = cache.createKey("How did I sleep?", today, "fp1")
        cache.put(
            key = key,
            response = "You slept 7 hours and 30 minutes.",
            dataReferences = sampleReferences,
            domainTag = DataReferenceDomain.SLEEP
        )

        val cached = cache.get(key)
        assertNotNull(cached)
        assertEquals("You slept 7 hours and 30 minutes.", cached?.response)
        assertEquals(1, cache.hitCount)
    }

    @Test
    fun invalidateDomain_evictsOnlyMatchingDomain() {
        val sleepKey = cache.createKey("sleep query", today, "fp_sleep")
        val stepKey = cache.createKey("step query", today, "fp_step")

        val stepRefs = listOf(
            DataReference(
                domain = DataReferenceDomain.STEPS,
                label = "Steps",
                value = "8,400"
            )
        )

        cache.put(sleepKey, "Sleep response", sampleReferences, DataReferenceDomain.SLEEP)
        cache.put(stepKey, "Step response", stepRefs, DataReferenceDomain.STEPS)

        // Invalidate SLEEP domain
        cache.invalidateDomain(DataReferenceDomain.SLEEP)

        assertNull(cache.get(sleepKey))
        assertNotNull(cache.get(stepKey))
    }

    @Test
    fun clear_evictsAllEntries() {
        val key1 = cache.createKey("query 1", today, "fp1")
        val key2 = cache.createKey("query 2", today, "fp2")

        cache.put(key1, "resp 1", emptyList())
        cache.put(key2, "resp 2", emptyList())

        cache.clear()

        assertNull(cache.get(key1))
        assertNull(cache.get(key2))
        assertEquals(0, cache.size())
    }

    @Test
    fun expiration_evictsStaleEntries() {
        val shortTtlCache = AssistantCache(ttlMs = 100L)
        val key = shortTtlCache.createKey("query", today, "fp")

        val now = 1000L
        shortTtlCache.put(key, "old response", emptyList(), nowMs = now)

        // Read after TTL expired
        val cached = shortTtlCache.get(key, nowMs = now + 200L)
        assertNull(cached)
    }
}
