package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.AddonStreams
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
    private val success = NetworkResult.Success(listOf(AddonStreams("source", null, emptyList())))

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
