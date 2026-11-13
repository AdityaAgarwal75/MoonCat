package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddonStreamRequestTest {
    @Test fun `queue wait does not consume request timeout`() = runTest {
        val slots = Semaphore(1, 1)
        val result = async { requestAddonStreams(slots) { delay(14_000); NetworkResult.Success("ok") } }
        delay(20_000)
        slots.release()
        assertEquals(NetworkResult.Success("ok"), result.await())
    }

    @Test fun `transient failures get one retry`() = runTest {
        for (code in listOf(null, 408, 500, 502, 503, 504)) {
            var calls = 0
            val result = requestAddonStreams(Semaphore(1)) {
                if (++calls == 1) NetworkResult.Error("temporary", code) else NetworkResult.Success("ok")
            }
            assertEquals(NetworkResult.Success("ok"), result)
            assertEquals(2, calls)
        }
    }

    @Test fun `permanent errors and empty success are not retried`() = runTest {
        for (expected in listOf(NetworkResult.Error("missing", 404), NetworkResult.Error("auth", 401),
            NetworkResult.Error("limited", 429), NetworkResult.Success(emptyList<String>()))) {
            var calls = 0
            assertEquals(expected, requestAddonStreams(Semaphore(1)) { calls++; expected })
            assertEquals(1, calls)
        }
    }

    @Test fun `timeouts are bounded and release permits`() = runTest {
        val slots = Semaphore(1)
        var calls = 0
        val result = requestAddonStreams<String>(slots) { calls++; awaitCancellation() }
        assertEquals(NetworkResult.Error("Source timed out"), result)
        assertEquals(2, calls)
        assertEquals(1, slots.availablePermits)
        assertEquals(30_500L, testScheduler.currentTime)
    }

    @Test fun `cancellation is propagated without retry`() = runTest {
        val slots = Semaphore(1)
        var calls = 0
        val result = async { requestAddonStreams<String>(slots) { calls++; awaitCancellation() } }
        runCurrent()
        result.cancel()
        result.join()
        assertTrue(result.isCancelled)
        assertEquals(1, calls)
        assertEquals(1, slots.availablePermits)
    }
    @Test fun `interactive source search has a total budget including queueing and retry`() = runTest {
        val slots = Semaphore(1, 1)
        val result = requestAddonStreams<String>(slots, totalTimeoutMs = 8_000L, attemptTimeoutMs = 3_500L) {
            error("must not run while queued")
        }
        assertEquals(NetworkResult.Error("Source timed out"), result)
        assertEquals(8_000L, testScheduler.currentTime)
    }

}
