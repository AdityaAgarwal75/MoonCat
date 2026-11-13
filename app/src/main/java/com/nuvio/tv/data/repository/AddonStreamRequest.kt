package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Queueing does not consume a source's request budget. Retry only transient failures. */
internal suspend fun <T> requestAddonStreams(
    slots: Semaphore,
    totalTimeoutMs: Long = 60_000L,
    attemptTimeoutMs: Long = 15_000L,
    request: suspend () -> NetworkResult<T>
): NetworkResult<T> = withTimeoutOrNull(totalTimeoutMs) {
    repeat(2) { attempt ->
        val result = slots.withPermit {
            withTimeoutOrNull(attemptTimeoutMs) { request() }
                ?: NetworkResult.Error("Source timed out")
        }
        val transient = result is NetworkResult.Error &&
            (result.code == null || result.code in setOf(408, 500, 502, 503, 504))
        if (!transient || attempt == 1) return@withTimeoutOrNull result
        // Release the slot during backoff so other sources can make progress.
        delay(500L)
    }
    error("Unreachable")
} ?: NetworkResult.Error("Source timed out")
