package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.core.player.playbackRequestKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Race bounded HTTP samples and torrent preloads; discovery can continue while probing. */
class PlaybackSourceSession(
    private val languageGraceMs: Long = 300L,
    private val torrentReadyTimeoutMs: Long = 7_000L
) {
    data class Snapshot(val candidates: List<Stream> = emptyList(), val complete: Boolean = false)

    suspend fun select(
        snapshots: StateFlow<Snapshot>,
        language: String,
        probeDirect: suspend (List<Stream>) -> Stream?,
        prepareTorrent: suspend (List<Stream>) -> Stream?
    ): Stream? = coroutineScope {
        val first = snapshots.first { it.candidates.isNotEmpty() || it.complete }
        if (first.candidates.isEmpty()) return@coroutineScope null
        withTimeoutOrNull(languageGraceMs) {
            snapshots.first { snapshot -> snapshot.complete || snapshot.candidates.any {
                MoonCatSourcePolicy.languageTier(it, language) == 0
            } }
        }
        val attempted = mutableSetOf<String>()
        while (true) {
            val snapshot = snapshots.first { state ->
                state.complete || state.candidates.any { it.playbackRequestKey() !in attempted }
            }
            val candidates = probeBatch(snapshot.candidates.filter { it.playbackRequestKey() !in attempted }, language)
            // These routes must be resolved by the normal player, not an HTTP sample.
            candidates.firstOrNull { it.serverTarget != null || it.isYouTube() ||
                (it.isDirectDebrid() && it.getStreamUrl() == null) }?.let { return@coroutineScope it }
            if (candidates.isEmpty()) return@coroutineScope null
            val direct = candidates.filterNot { it.isTorrent() }.filter { it.getStreamUrl() != null }
            val torrents = candidates.filter { it.isTorrent() }.take(MoonCatTorrentRace.MAX_CANDIDATES)
            val ignored = candidates.filterNot { it in direct || it.isTorrent() }
            attempted.addAll((direct + torrents + ignored).map { it.playbackRequestKey() })
            val results = Channel<Stream?>(2)
            val jobs = listOfNotNull(
                direct.takeIf { it.isNotEmpty() }?.let { sources -> launch {
                    val result = try { withTimeoutOrNull(5_000L) { probeDirect(sources) } }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    results.send(result)
                } },
                torrents.takeIf { it.isNotEmpty() }?.let { sources -> launch {
                    val result = try { withTimeoutOrNull(torrentReadyTimeoutMs) { prepareTorrent(sources) } }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    results.send(result)
                } }
            )
            try {
                repeat(jobs.size) { results.receive()?.let { return@coroutineScope it } }
            } finally {
                jobs.forEach { it.cancel() }
                results.close()
            }
            // If no preload is ready, let the normal player start the best HD torrent.
            // Manual selection already uses this same playback path. Metadata can take
            // longer than the bounded race, and the player has its own failure recovery.
            val fallback = MoonCatSourcePolicy.readinessOrder(candidates.filter { it.isTorrent() }, language)
                .firstOrNull()
            if (fallback != null) return@coroutineScope fallback
            if (snapshot.complete && snapshots.value.candidates.none { it.playbackRequestKey() !in attempted }) {
                return@coroutineScope null
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    /** Reserve slots for both transports and language fallbacks before filling the batch.
     * Ten dead Hindi files must not crowd a healthy English source out of the race.
     */
    internal fun probeBatch(streams: List<Stream>, language: String): List<Stream> {
        val ranked = MoonCatSourcePolicy.readinessOrder(streams, language)
            .distinctBy { it.playbackRequestKey() }
        val batch = linkedMapOf<String, Stream>()
        for (torrent in listOf(false, true)) {
            for (preferred in listOf(true, false)) {
                ranked.firstOrNull { it.isTorrent() == torrent &&
                    (MoonCatSourcePolicy.languageTier(it, language) == 0) == preferred }
                    ?.let { batch[it.playbackRequestKey()] = it }
            }
        }
        ranked.forEach { if (batch.size < 8) batch[it.playbackRequestKey()] = it }
        return batch.values.toList()
    }

}
