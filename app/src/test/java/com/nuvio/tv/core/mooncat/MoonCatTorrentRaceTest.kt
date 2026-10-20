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
        assertEquals(4, count)
    }
    @Test fun `unresponsive probes time out instead of holding the player indefinitely`() = runTest {
        assertNull(MoonCatTorrentRace.choose(listOf(torrent("stuck"))) { delay(100000); true })
        assertEquals(4500L, testScheduler.currentTime)
    }
}
