package com.nuvio.tv.core.mooncat

import org.junit.Assert.*
import org.junit.Test

class PlaybackPreferencesTest {
    @Test fun `dead preferred swarms cannot exhaust retries before a viable original`() {
        val dead = (1..10).map { source("Hindi 1080p 2 GB seeds: 0").copy(infoHash = "dead$it") }
        val original = source("Korean 1080p 2 GB seeds: 100").copy(infoHash = "live")
        assertEquals(original, PlaybackPreferences.order(dead + original, "ko", false).first())
    }

    @Test fun `anime IDs retain English dub preference before genres load`() {
        assertTrue(PlaybackPreferences.isAnime(null, "ja", "kitsu:123:1:1"))
        assertFalse(PlaybackPreferences.isAnime(null, "ja", "tt123"))
    }

    @Test fun `autoplay accepts sub one GB 1080p releases up to six GB`() {
        listOf("720p 2 GB", "2160p 5 GB", "1080p 8 GB", "Unknown 2 GB", "1080p").forEach {
            assertFalse(it, PlaybackPreferences.eligible(source(it).copy(infoHash = "hash")))
        }
        listOf("1080p 250 MB", "1080p 900 MB", "1080p 1 GB", "1080p 5 GB", "1080p 6 GB").forEach {
            assertTrue(it, PlaybackPreferences.eligible(source(it).copy(infoHash = "hash")))
        }
    }

    @Test fun `sub one GB release remains an autoplay candidate`() {
        val small = source("Hindi 1080p 700 MB seeds: 50").copy(infoHash = "small")
        val large = source("Hindi 1080p 6.5 GB seeds: 500").copy(infoHash = "large")
        assertEquals(listOf(small), PlaybackPreferences.order(listOf(large, small), "en", false))
    }

    @Test fun `Hindi movies fall back to original audio before another dub`() {
        val english = source("English 1080p 2 GB").copy(infoHash = "en")
        val original = source("Korean 1080p 2 GB").copy(infoHash = "ko")
        val hindi = source("Hindi 1080p 2 GB").copy(infoHash = "hi")
        assertEquals(listOf(hindi, original), PlaybackPreferences.order(listOf(english, original, hindi), "ko", false))
        assertEquals(original, PlaybackPreferences.order(listOf(english, original), "ko", false).first())
        assertTrue(PlaybackPreferences.order(listOf(english), "ko", false).isEmpty())
    }

    @Test fun `anime prefers English dub over Japanese and keeps Japanese fallback`() {
        val original = source("Japanese 1080p 2 GB").copy(infoHash = "ja")
        val dub = source("English 1080p 2 GB").copy(infoHash = "en")
        assertEquals(dub, PlaybackPreferences.order(listOf(original, dub), "ja", true).first())
        assertEquals(original, PlaybackPreferences.order(listOf(original), "ja", true).first())
    }

    @Test fun `well seeded five GB release is not rejected by the old four GB cap`() {
        val large = source("Hindi 1080p 5 GB seeds: 500").copy(infoHash = "large")
        val small = source("Hindi 1080p 1.1 GB seeds: 2").copy(infoHash = "small")
        assertEquals(large, PlaybackPreferences.order(listOf(small, large), "en", false).first())
    }
    @Test fun `container audio preference follows the same dub policy as source selection`() {
        assertEquals(listOf("hi", "ko"), PlaybackPreferences.audioLanguages("ko", false))
        assertEquals(listOf("hi", "en", "ja"), PlaybackPreferences.audioLanguages("ja", true))
    }

}
