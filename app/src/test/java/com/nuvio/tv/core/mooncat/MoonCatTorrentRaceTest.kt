package com.nuvio.tv.core.mooncat

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MoonCatTorrentRaceTest {
    private fun torrent(id: String) = source("1080p Hindi $id").copy(infoHash = id)
    @Test fun `first verified buffered torrent wins and slower work is cancelled`() = runTest {
        val slow = torrent("slow"); val fast = torrent("fast")
        var slowCancelled = false
        val winner = MoonCatTorrentRace.choose(listOf(slow, fast)) {
            if (it == slow) try { delay(10000); true } finally { slowCancelled = true }
            else { delay(100); true }
        }
        assertEquals(fast, winner)
        assertTrue(slowCancelled)
    }
    @Test fun `failed probes do not prevent a good source from winning`() = runTest {
        val bad = torrent("bad"); val good = torrent("good")
        assertEquals(good, MoonCatTorrentRace.choose(listOf(bad, good)) { if (it == bad) error("unreachable") else true })
    }
    @Test fun `torrent probe count is bounded and duplicate files are probed once`() = runTest {
        val streams = (1..10).map { torrent(it.toString()) }
        var count = 0
        assertNull(MoonCatTorrentRace.choose(listOf(streams[0]) + streams) { count++; false })
        assertEquals(MoonCatTorrentRace.MAX_CANDIDATES, count)
    }
    @Test fun `unresponsive probes time out instead of holding the player indefinitely`() = runTest {
        assertNull(MoonCatTorrentRace.choose(listOf(torrent("stuck"))) { delay(100000); true })
        assertEquals(MoonCatTorrentRace.DEADLINE_MS, testScheduler.currentTime)
    }

    @Test fun `Hindi ready within grace wins over faster English`() = runTest {
        val hindi = source("1080p Hindi").copy(infoHash = "hi")
        val english = source("1080p English").copy(infoHash = "en")
        assertEquals(hindi, MoonCatTorrentRace.choose(listOf(english, hindi)) {
            delay(if (it == english) 50 else 400); true
        })
    }

    @Test fun `ready HD wins within the bounded quality grace`() = runTest {
        val hd = source("1080p Hindi").copy(infoHash = "hd")
        val small = source("720p Hindi").copy(infoHash = "small")
        assertEquals(hd, MoonCatTorrentRace.choose(listOf(small, hd)) {
            delay(if (it == small) 50 else 300); true
        })
    }

    @Test fun `failed Hindi falls back to a verified English torrent`() = runTest {
        val hindi = source("1080p Hindi").copy(infoHash = "hi")
        val english = source("720p English").copy(infoHash = "en")
        assertEquals(english, MoonCatTorrentRace.choose(listOf(hindi, english)) { it == english })
    }
    @Test fun `preferred HD starts immediately without a quality grace delay`() = runTest {
        val hd = torrent("hd")
        val slow = torrent("slow")
        assertEquals(hd, MoonCatTorrentRace.choose(listOf(hd, slow)) {
            if (it == slow) delay(10000) else delay(50)
            true
        })
        assertEquals(50L, testScheduler.currentTime)
    }

    @Test fun `fallback language is probed even behind many preferred releases`() = runTest {
        val preferred = (1..8).map { torrent(it.toString()) }
        val fallback = source("1080p English").copy(infoHash = "english")
        assertEquals(fallback, MoonCatTorrentRace.choose(preferred + fallback) { it == fallback })
    }

}
