package com.nuvio.tv.core.mooncat

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MetadataSourceSelectorTest {
    @Test fun `later preferred source replaces first fallback within bounded window`() = runTest {
        val english = source("English 1080p")
        val hindi = source("Hindi 1080p")
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(english)))
        val selected = async { MetadataSourceSelector.select(state, "hi") }
        advanceTimeBy(400)
        state.value = PlaybackSourceSession.Snapshot(listOf(hindi, english))
        runCurrent()
        assertEquals(hindi, selected.await())
        assertEquals(400L, testScheduler.currentTime)
    }

    @Test fun `preferred audio still gives other fast providers a short comparison window`() = runTest {
        val low = source("Hindi 720p")
        val high = source("Hindi 1080p")
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(low)))
        val selected = async { MetadataSourceSelector.select(state, "hi") }
        advanceTimeBy(100)
        state.value = PlaybackSourceSession.Snapshot(listOf(high, low))
        assertEquals(high, selected.await())
        assertEquals(MetadataSourceSelector.SETTLE_MS, testScheduler.currentTime)
    }

    @Test fun `complete search and cached results return without delay`() = runTest {
        val item = source("English 1080p")
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(item), complete = true))
        assertEquals(item, MetadataSourceSelector.select(state, "hi"))
        assertEquals(0L, testScheduler.currentTime)
        state.value = PlaybackSourceSession.Snapshot(complete = true)
        assertNull(MetadataSourceSelector.select(state, "hi"))
    }

    @Test fun `slow provider never holds English fallback longer than language grace`() = runTest {
        val item = source("English 1080p")
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(item)))
        assertEquals(item, MetadataSourceSelector.select(state, "hi"))
        assertEquals(MetadataSourceSelector.LANGUAGE_GRACE_MS, testScheduler.currentTime)
    }

    @Test fun `selection ends immediately when all providers finish during settle`() = runTest {
        val item = source("Hindi 1080p")
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(item)))
        val selected = async { MetadataSourceSelector.select(state, "hi") }
        advanceTimeBy(50)
        state.value = state.value.copy(complete = true)
        assertEquals(item, selected.await())
        assertEquals(50L, testScheduler.currentTime)
    }

    @Test fun `leaving screen cancels selection before it can emit a route`() = runTest {
        val state = MutableStateFlow(PlaybackSourceSession.Snapshot(listOf(source("English 1080p"))))
        val selected = async { MetadataSourceSelector.select(state, "hi") }
        runCurrent()
        selected.cancelAndJoin()
        assertTrue(selected.isCancelled)
    }
}
