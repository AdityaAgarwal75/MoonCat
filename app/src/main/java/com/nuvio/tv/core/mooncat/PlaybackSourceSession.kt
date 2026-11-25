package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.core.player.playbackRequestKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Briefly collect competing provider results; never run bandwidth/preload races here.
     * Selection uses the latest snapshot so a later popular source beats an early weak one.
     */
    suspend fun selectMetadata(snapshots: StateFlow<Snapshot>, language: String, preserveOrder: Boolean = false): Stream? {
        val first = snapshots.first { it.candidates.isNotEmpty() || it.complete }
        if (first.candidates.isEmpty()) return null
        if (!first.complete) {
            withTimeoutOrNull(languageGraceMs) { snapshots.first { it.complete } }
        }
        return withContext(Dispatchers.Default) {
            val candidates = snapshots.value.candidates
            (if (preserveOrder) candidates else MoonCatSourcePolicy.instantOrder(candidates, language)).firstOrNull()
        }
    }

    suspend fun select(
        snapshots: StateFlow<Snapshot>,
        language: String,
        probeDirect: suspend (List<Stream>) -> Stream?,
        prepareTorrent: suspend (List<Stream>) -> Stream?
    ): Stream? = coroutineScope {
        val first = snapshots.first { it.candidates.isNotEmpty() || it.complete }
        if (first.candidates.isEmpty()) return@coroutineScope null
        if (!first.complete) {
            withTimeoutOrNull(languageGraceMs) { snapshots.first { it.complete } }
        }
        val attempted = mutableSetOf<String>()
        var torrentFallback: Stream? = null
        while (true) {
            val snapshot = snapshots.first { state ->
                state.complete || state.candidates.any { it.playbackRequestKey() !in attempted }
            }
            val candidates = probeBatch(snapshot.candidates.filter { it.playbackRequestKey() !in attempted }, language)
            // These routes must be resolved by the normal player, not an HTTP sample.
            candidates.firstOrNull { it.serverTarget != null || it.isYouTube() ||
                (it.isDirectDebrid() && it.getStreamUrl() == null) }?.let { return@coroutineScope it }
            if (candidates.isEmpty()) return@coroutineScope torrentFallback
            val direct = candidates.filterNot { it.isTorrent() }.filter { it.getStreamUrl() != null }
            val torrentCandidates = candidates.filter { it.isTorrent() }
            // Let a second provider compete even if the first supplies most of the shortlist.
            val torrents = (torrentCandidates.distinctBy { it.addonName }.take(2) + torrentCandidates)
                .distinctBy { it.playbackRequestKey() }.take(MoonCatTorrentRace.MAX_CANDIDATES)
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
            if (torrentFallback == null) torrentFallback = torrents.firstOrNull()
            val latest = snapshots.value
            if (latest.candidates.none { it.playbackRequestKey() !in attempted }) {
                if (torrentFallback != null || latest.complete) return@coroutineScope torrentFallback
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    /** Reserve slots for both transports and language fallbacks before filling the batch.
     * Ten dead Hindi files must not crowd a healthy English source out of the race.
     */
    internal fun probeBatch(streams: List<Stream>, language: String, limit: Int = 8): List<Stream> {
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
        for (torrent in listOf(false, true)) {
            ranked.firstOrNull { it.isTorrent() == torrent &&
                MoonCatSourcePolicy.languageTier(it, language) == 0 &&
                MoonCatSourcePolicy.qualityTier(it) == 0 }
                ?.let { batch[it.playbackRequestKey()] = it }
        }
        ranked.forEach { if (batch.size < limit) batch[it.playbackRequestKey()] = it }
        return batch.values.take(limit)

    }

}
