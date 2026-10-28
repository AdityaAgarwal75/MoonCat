package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Compare bounded verified preloads in one shared engine, with a short quality grace period. */
object MoonCatTorrentRace {
    const val MAX_CANDIDATES = 4
    const val DEADLINE_MS = 10_000L
    const val QUALITY_GRACE_MS = 700L
    suspend fun choose(candidates: List<Stream>, preferredLanguage: String = "hi", allow4k: Boolean = false, probe: suspend (Stream) -> Boolean): Stream? = coroutineScope {
        val ranked = MoonCatSourcePolicy.playbackCandidates(candidates, preferredLanguage, allow4k).filter { it.isTorrent() }
            .distinctBy { "${it.getEffectiveInfoHash()}:${it.getEffectiveFileIdx()}" }.take(MAX_CANDIDATES)
        val results = Channel<Stream?>(Channel.UNLIMITED)
        val jobs = ranked.map { candidate ->
            launch {
                var ready: Stream? = null
                try {
                    if (withTimeoutOrNull(DEADLINE_MS) { probe(candidate) } == true) ready = candidate
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* A failed candidate must not cancel the other probes. */ }
                finally { results.trySend(ready) }
            }
        }
        try {
            var completed = 0
            var first: Stream? = null
            while (completed < jobs.size && first == null) {
                first = results.receive()
                completed++
            }
            if (first == null) return@coroutineScope null
            val ready = mutableListOf(first)
            // Brief quality grace only. Missing preferred audio must not add a
            // second full provider deadline after a playable file is ready.
            val grace = QUALITY_GRACE_MS
            withTimeoutOrNull(grace) {
                while (completed < jobs.size) {
                    results.receive()?.let { ready.add(it) }
                    completed++
                }
            }
            ready.minWithOrNull(compareBy<Stream> { MoonCatSourcePolicy.languageTier(it, preferredLanguage) }
                .thenBy(MoonCatSourcePolicy::qualityTier).thenBy(MoonCatSourcePolicy::releaseTier))
        } finally { jobs.forEach { it.cancel() } }
    }
}
