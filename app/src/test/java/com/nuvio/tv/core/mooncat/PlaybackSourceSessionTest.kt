package com.nuvio.tv.core.mooncat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSourceSessionTest {
    @Test fun `direct and torrent probes race and slower work is cancelled`() = runTest {
        val http = source("1080p Hindi", "https://cdn.example/video")
        val torrent = source("1080p Hindi").copy(infoHash = "torrent")
        var cancelled = false
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(http, torrent), true))
        val winner = PlaybackSourceSession().select(states, "hi", {
            try { awaitCancellation() } finally { cancelled = true }
        }, { delay(100); torrent })
        assertEquals(torrent, winner)
        assertTrue(cancelled)
    }

    @Test fun `timed out torrent probes fall back to normal player`() = runTest {
        val torrent = source("1080p Hindi").copy(infoHash = "fallback-hash")
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(torrent), true))
        assertEquals(torrent, PlaybackSourceSession().select(states, "hi", { null }, { null }))
    }

    @Test fun `new results arriving during a failed probe are tested`() = runTest {
        val bad = source("1080p Hindi", "https://cdn.example/bad")
        val good = source("1080p Hindi", "https://cdn.example/good")
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(bad)))
        val choice = async { PlaybackSourceSession().select(states, "hi", { batch ->
            if (bad in batch) { delay(500); null } else good
        }, { null }) }
        runCurrent()
        advanceTimeBy(200)
        states.value = PlaybackSourceSession.Snapshot(listOf(bad, good), true)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(good, choice.await())
    }

    @Test fun `completed empty search does not wait`() = runTest {
        assertNull(PlaybackSourceSession().select(
            MutableStateFlow(PlaybackSourceSession.Snapshot(complete = true)), "hi", { null }, { null }))
    }
    @Test fun `probe batch reserves other transport and fallback language and deduplicates`() {
        val preferred = (1..10).map { source("1080p Hindi $it").copy(infoHash = "hash$it") }
        val english = source("1080p English").copy(infoHash = "english")
        val http = source("1080p Hindi", "https://cdn.example/video")
        val batch = PlaybackSourceSession().probeBatch(preferred + preferred + english + http, "hi")
        assertEquals(8, batch.size)
        assertTrue(english in batch)
        assertTrue(http in batch)
        assertEquals(batch.size, batch.distinct().size)
    }

    @Test fun `YouTube route is handed to the player instead of silently discarded`() = runTest {
        val youtube = source("1080p Hindi").copy(ytId = "video-id")
        assertEquals(youtube, PlaybackSourceSession().select(
            MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(youtube), true)), "hi",
            { error("must use player resolver") }, { error("not a torrent") }))
    }

    @Test fun `torrent fallback chooses availability hint over a dead high quality release`() = runTest {
        val dead = source("1080p Hindi seeds: 0").copy(infoHash = "dead")
        val healthy = source("720p Hindi seeds: 100").copy(infoHash = "healthy")
        assertEquals(healthy, PlaybackSourceSession().select(
            MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(dead, healthy), true)), "hi", { null }, { null }))
    }

    @Test fun `discovery shortlist retains fallback beyond thirty two preferred results`() {
        val preferred = (1..40).map { source("720p Hindi $it").copy(infoHash = "hash$it") }
        val english = source("1080p English").copy(infoHash = "english")
        val hd = source("1080p Hindi").copy(infoHash = "hd")
        val batch = PlaybackSourceSession().probeBatch(preferred + english + hd, "hi", limit = 32)
        assertEquals(32, batch.size)
        assertTrue(english in batch)
        assertTrue(hd in batch)
    }

}
