package com.nuvio.tv.core.player

import com.nuvio.tv.core.mooncat.source
import org.junit.Assert.*
import org.junit.Test

class SourceFailoverSessionTest {
    @Test fun `dead source and duplicate urls are excluded across providers`() {
        val session = SourceFailoverSession()
        assertTrue(session.begin("movie:a", "https://dead/video", null, null))
        val live = source("English 1080p", "https://live/video")
        assertEquals(listOf(live), session.candidates(listOf(
            source("Hindi 1080p", "https://dead/video"), live, live.copy(addonName = "Duplicate")
        ), "hi"))
        session.attempted(live)
        assertTrue(session.candidates(listOf(live), "hi").isEmpty())
    }

    @Test fun `English and unknown fallback are kept when Hindi is absent`() {
        val session = SourceFailoverSession()
        session.begin("a", null, null, null)
        listOf("English", "Unknown").forEach { name ->
            val fallback = source("$name 1080p", "https://live/$name")
            assertEquals(listOf(fallback), session.candidates(listOf(fallback), "hi"))
        }
    }

    @Test fun `failure budget is finite and resets for a different episode`() {
        val session = SourceFailoverSession(maxAttempts = 2)
        assertTrue(session.begin("series:1:1", "https://a", null, null))
        assertTrue(session.begin("series:1:1", "https://b", null, null))
        assertFalse(session.begin("series:1:1", "https://c", null, null))
        assertTrue(session.begin("series:1:2", "https://c", null, null))
        val previous = source("1080p", "https://a")
        assertEquals(listOf(previous), session.candidates(listOf(previous), "hi"))
    }

    @Test fun `torrent identity includes file index and does not retry same file`() {
        val session = SourceFailoverSession()
        val torrent = source("1080p").copy(infoHash = "ABC", fileIdx = 1)
        session.begin("series:1:1", null, "abc", 1)
        assertTrue(session.candidates(listOf(torrent), "hi").isEmpty())
        assertEquals(listOf(torrent.copy(fileIdx = 2)), session.candidates(listOf(torrent.copy(fileIdx = 2)), "hi"))
    }
    @Test fun `dead CDN must not exclude a different direct route for the same torrent`() {
        val session = SourceFailoverSession()
        session.begin("movie:a", "https://dead/video", "abc", 1)
        val alternative = source("Hindi 1080p", "https://live/video").copy(infoHash = "abc", fileIdx = 1)
        assertEquals(listOf(alternative), session.candidates(listOf(alternative), "hi"))
    }

    @Test fun `headers and transport form part of request identity without exposing secrets`() {
        val url = "https://live/video?token=private"
        val first = playbackRequestKey(url, "abc", 1, mapOf("Authorization" to "first-secret"))
        val second = playbackRequestKey(url, "abc", 1, mapOf("authorization" to "second-secret"))
        assertNotEquals(first, second)
        assertEquals(first, playbackRequestKey(url, "other-hash", 99, mapOf("authorization" to "first-secret")))
        assertFalse(first.contains("private"))
        assertFalse(first.contains("secret"))
        assertEquals(64, first.length)
        assertNotEquals(first, playbackRequestKey(null, "abc", 1))
    }
    @Test fun `failed local torrent URL also excludes its original hash and file`() {
        val session = SourceFailoverSession()
        session.begin("episode", "http://127.0.0.1:8080/stream/abc", "abc", 1, localTorrent = true)
        val dead = source("Hindi 1080p").copy(infoHash = "abc", fileIdx = 1)
        val live = dead.copy(infoHash = "def")
        assertEquals(listOf(live), session.candidates(listOf(dead, live), "hi"))
    }
    @Test fun `userinfo and player normalized auth share failover identity`() {
        val raw = "https://user:password@cdn.test/movie?token=private"
        val headers = mapOf("Authorization" to "Basic dXNlcjpwYXNzd29yZA==")
        val normalized = "https://cdn.test/movie?token=private"
        assertEquals(playbackRequestKey(raw, null, null), playbackRequestKey(normalized, null, null, headers))
        val session = SourceFailoverSession()
        session.begin("movie", normalized, null, null, headers)
        assertTrue(session.candidates(listOf(source("Hindi 1080p", raw)), "hi").isEmpty())
    }
}
