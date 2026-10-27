package com.nuvio.tv.core.mooncat

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSourceSessionTest {
    private val english = source("English 1080p", "https://cdn.example/english")
    private val hindi = source("Hindi 720p", "https://cdn.example/hindi")

    @Test fun `late preferred source wins without blocking discovery or probing HD`() = runTest {
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(english)))
        val choice = async {
            PlaybackSourceSession().select(states, "hi", { fail("HD must not be sampled"); null }, { null })
        }
        runCurrent()
        advanceTimeBy(500)
        states.value = PlaybackSourceSession.Snapshot(listOf(hindi, english))
        runCurrent()
        assertEquals(hindi, choice.await())
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test fun `completed search uses its fallback immediately`() = runTest {
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(english), true))
        assertEquals(english, PlaybackSourceSession().select(states, "hi", { null }, { null }))
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `unknown or other language never holds playback beyond grace`() = runTest {
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(english)))
        assertEquals(english, PlaybackSourceSession().select(states, "hi", { null }, { null }))
        assertEquals(1_200L, testScheduler.currentTime)
    }

    @Test fun `failed UHD qualification falls back to HD and never unchecked UHD`() = runTest {
        val uhd = source("Hindi 2160p", "https://cdn.example/uhd")
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(uhd, hindi), true))
        assertEquals(hindi, PlaybackSourceSession().select(states, "hi", { throw IOException() }, { null }))
        states.value = states.value.copy(candidates = listOf(uhd))
        assertNull(PlaybackSourceSession().select(states, "hi", { null }, { null }))
    }

    @Test fun `native setup is bounded and late direct preferred result remains eligible`() = runTest {
        val torrent = source("Hindi 1080p")
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(torrent, english), true))
        val choice = async {
            PlaybackSourceSession().select(states, "hi", { null }, { awaitCancellation() })
        }
        runCurrent()
        advanceTimeBy(500)
        states.value = PlaybackSourceSession.Snapshot(listOf(hindi, torrent, english), true)
        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(hindi, choice.await())
        assertEquals(4_000L, testScheduler.currentTime)
    }

    @Test fun `canceling playback request cancels native preparation`() = runTest {
        val states = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(source("Hindi 1080p")), true))
        var stopped = false
        val choice = async {
            PlaybackSourceSession().select(states, "hi", { null }, {
                try { awaitCancellation() } finally { stopped = true }
            })
        }
        runCurrent()
        choice.cancel()
        runCurrent()
        assertTrue(stopped)
        assertTrue(choice.isCancelled)
    }

    @Test fun `completed empty search does not wait for a nonexistent candidate`() = runTest {
        assertNull(PlaybackSourceSession().select(
            MutableStateFlow(PlaybackSourceSession.Snapshot(complete = true)), "hi", { null }, { null }))
    }
}
