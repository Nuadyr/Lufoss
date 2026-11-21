package io.github.nuadyr.lufoss

import io.github.nuadyr.lufoss.data.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {
    private val hour = 3_600L

    @Test
    fun missingHeaderGetsOneHour() {
        assertEquals("max-age=3600", CachePolicy.capCacheControl(null, hour))
        assertEquals("max-age=3600", CachePolicy.capCacheControl("", hour))
    }

    @Test
    fun longerServerLifetimesAreCapped() {
        assertEquals("max-age=3600", CachePolicy.capCacheControl("max-age=604800", hour))
        assertEquals("public, max-age=3600", CachePolicy.capCacheControl("public, max-age=86400, immutable", hour))
    }

    @Test
    fun shorterServerLifetimesAreKept() {
        assertEquals("max-age=60", CachePolicy.capCacheControl("max-age=60", hour))
        assertEquals("max-age=0", CachePolicy.capCacheControl("max-age=-5", hour))
    }

    @Test
    fun noStoreIsNeverOverridden() {
        assertEquals("no-store", CachePolicy.capCacheControl("no-store", hour))
        assertEquals("private, No-Store", CachePolicy.capCacheControl("private, No-Store", hour))
    }

    @Test
    fun sharedCacheLifetimeIsDropped() {
        assertEquals("max-age=3600", CachePolicy.capCacheControl("s-maxage=999999", hour))
    }

    @Test
    fun purgeIsDueAfterAnHourOrWhenTheClockGoesBack() {
        val ttl = hour * 1000
        assertFalse(CachePolicy.isExpired(1_000L, 1_000L + ttl - 1, ttl))
        assertTrue(CachePolicy.isExpired(1_000L, 1_000L + ttl, ttl))
        assertTrue(CachePolicy.isExpired(5_000L, 1_000L, ttl))
        assertTrue(CachePolicy.isExpired(0L, 1_000L + ttl, ttl))
    }
}
