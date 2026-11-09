package com.nuvio.tv.core.catalog

import org.junit.Assert.*
import org.junit.Test

class CatalogResponseCacheTest {
    @Test fun `least recently viewed rows leave first without flushing hot rows`() {
        val cache = CatalogResponseCache(maxEntries = 2)
        cache.put("home", "h"); cache.put("old", "o")
        assertEquals("h", cache.get("home"))
        cache.put("new", "n")
        assertNull(cache.get("old"))
        assertEquals("h", cache.get("home"))
        assertEquals("n", cache.get("new"))
    }

    @Test fun `weight limit applies even when entry count is small`() {
        val cache = CatalogResponseCache(maxBytes = 20)
        cache.put("a", "1234"); cache.put("b", "1234"); cache.put("c", "1234")
        assertNull(cache.get("a"))
        assertEquals("1234", cache.get("b"))
        assertEquals("1234", cache.get("c"))
        cache.put("huge", "x".repeat(100))
        assertNull(cache.get("huge"))
        assertEquals("1234", cache.get("b"))
    }

    @Test fun `expired rows are refreshed and replacements release old weight`() {
        var clock = 0L
        val cache = CatalogResponseCache(maxBytes = 20, ttlMs = 100, nowMs = { clock })
        cache.put("a", "12345678")
        cache.put("a", "1")
        cache.put("b", "1234")
        assertEquals("1", cache.get("a"))
        clock = 100
        assertNull(cache.get("a"))
        cache.put("c", "1234")
        assertNull(cache.get("b"))
        assertEquals("1234", cache.get("c"))
    }
}
