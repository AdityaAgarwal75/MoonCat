package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StreamSearchSessionCacheTest {
    private val key = StreamSearchRequestKey(1, "movie", "tt1", null, null, "config")
    private val source = Stream(
        name = "1080p",
        title = null,
        description = null,
        url = null,
        ytId = null,
        infoHash = "swarm",
        fileIdx = 0,
        externalUrl = null,
        behaviorHints = null,
        addonName = "source",
        addonLogo = null
    )
    private val success = NetworkResult.Success(listOf(AddonStreams("source", null, listOf(source))))

    @Test
    fun `retracted source is not restored after discovery reports an error`() = runTest {
        val cache = StreamSearchSessionCache(backgroundScope)
        val error = NetworkResult.Error("Source exceeded the size limit")
        val results = async {
            cache.observe(key, false) { flowOf(success, NetworkResult.Success(emptyList()), error) }.toList()
        }
        runCurrent()
        assertEquals(error, results.await().last())
        assertNull(cache.peek(key))
    }

    @Test
    fun `availability for stale metadata does not overwrite a newer source`() {
        val newer = source.copy(quality = "720p", qualityValue = 720)
        val checked = source.copy(debridCacheStatus = cachedStatus(2_000_000_000L))
        assertEquals(listOf(newer), applyDiscoveryAvailability(listOf(newer), listOf(source), listOf(checked)))
    }

    @Test
    fun `availability with a newly known oversized file removes the source`() {
        val checked = source.copy(debridCacheStatus = cachedStatus(7_000_000_000L))
        assertEquals(emptyList<Stream>(), applyDiscoveryAvailability(listOf(source), listOf(source), listOf(checked)))
    }

    private fun cachedStatus(size: Long) =
        StreamDebridCacheStatus("torbox", "TorBox", StreamDebridCacheState.CACHED, cachedSize = size)

    @Test
    fun `abandoned search cancels after transition grace`() = runTest {
        val stopped = CompletableDeferred<Unit>()
        val cache = StreamSearchSessionCache(backgroundScope, stopTimeoutMs = 100)
        val collector = launch {
            cache.observe(key, false) {
                flow {
                    try { emit(success); awaitCancellation() }
                    finally { stopped.complete(Unit) }
                }
            }.collect()
        }
        runCurrent()
        collector.cancelAndJoin()
        advanceTimeBy(99)
        assertFalse(stopped.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(stopped.isCompleted)
        assertNull(cache.peek(key))
    }

    @Test
    fun `player collector reuses search across navigation and keeps it active`() = runTest {
        var searches = 0
        val stopped = CompletableDeferred<Unit>()
        val cache = StreamSearchSessionCache(backgroundScope, stopTimeoutMs = 100)
        val producer = {
            flow<NetworkResult<List<AddonStreams>>> {
                searches++
                try { emit(success); awaitCancellation() }
                finally { stopped.complete(Unit) }
            }
        }
        val screen = launch { cache.observe(key, false, producer).collect() }
        runCurrent()
        screen.cancelAndJoin()
        advanceTimeBy(50)
        val player = launch { cache.observe(key, false, producer).collect() }
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, searches)
        assertFalse(stopped.isCompleted)
        player.cancelAndJoin()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(stopped.isCompleted)
    }

    @Test
    fun `completed results expire and refresh never reuses signed links`() = runTest {
        val cache = StreamSearchSessionCache(backgroundScope, nowMs = { testScheduler.currentTime }, completedTtlMs = 100)
        var searches = 0
        val producer = { searches++; flowOf<NetworkResult<List<AddonStreams>>>(success) }
        val first = async { cache.observe(key, false, producer).toList() }
        runCurrent()
        first.await()
        val second = async { cache.observe(key, false, producer).toList() }
        runCurrent()
        second.await()
        assertEquals(1, searches)
        val refreshed = async { cache.observe(key, true, producer).toList() }
        runCurrent()
        refreshed.await()
        assertEquals(2, searches)
        advanceTimeBy(100)
        assertNull(cache.peek(key))
    }

    @Test
    fun `refresh cancels obsolete producer without cancelling new session`() = runTest {
        val stopped = CompletableDeferred<Unit>()
        val cache = StreamSearchSessionCache(backgroundScope)
        val old = launch {
            cache.observe(key, false) {
                flow { try { emit(success); awaitCancellation() } finally { stopped.complete(Unit) } }
            }.collect()
        }
        runCurrent()
        val refreshed = async { cache.observe(key, true) { flowOf(success) }.toList() }
        runCurrent()
        refreshed.await()
        assertTrue(stopped.isCompleted)
        assertEquals(success.data, cache.peek(key))
        old.join()
    }
}
