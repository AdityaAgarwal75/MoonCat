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
}
