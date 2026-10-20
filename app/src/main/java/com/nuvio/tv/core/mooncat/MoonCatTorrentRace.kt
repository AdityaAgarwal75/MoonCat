package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** First verified preload wins. One shared torrent engine; no competing engine instances. */
object MoonCatTorrentRace {
    const val MAX_CANDIDATES = 4
    const val DEADLINE_MS = 4500L
    suspend fun choose(candidates: List<Stream>, probe: suspend (Stream) -> Boolean): Stream? = coroutineScope {
        val ranked = MoonCatSourcePolicy.rank(candidates).filter { it.isTorrent() }
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
            repeat(jobs.size) { results.receive()?.let { return@coroutineScope it } }
            null
        } finally { jobs.forEach { it.cancel() } }
    }
}
