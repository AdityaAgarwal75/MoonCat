package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonEpisodeIdentityTest {
    @Test fun `bare IMDb series IDs include the selected episode`() {
        assertEquals("tt1234567:1:210", addonVideoId("series", "tt1234567", 1, 210))
        assertEquals("tt1234567:0:1", addonVideoId("tv", "tt1234567", 0, 1))
    }
    @Test fun `existing episode IDs and addon namespaces are preserved`() {
        assertEquals("tt1234567:2:3", addonVideoId("series", "tt1234567:2:3", 2, 3))
        assertEquals("kitsu:123:5", addonVideoId("series", "kitsu:123:5", 1, 5))
        assertEquals("tmdb:123", addonVideoId("series", "tmdb:123", 1, 5))
    }
    @Test fun `live TV resources retain their addon type`() {
        assertEquals("tv", addonMediaType("tv"))
        assertEquals("series", addonMediaType("tv", 1, 20))
    }
    @Test fun `movies and incomplete episode selections are not fabricated`() {
        assertEquals("tt123", addonVideoId("movie", "tt123", 1, 2))
        assertEquals("tt123", addonVideoId("series", "tt123", null, 2))
        assertEquals("tt123", addonVideoId("series", "tt123", 1, 0))
        assertEquals("series", addonMediaType("SHOW"))
    }
}
