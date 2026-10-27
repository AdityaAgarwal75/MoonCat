package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One playback request. Discovery stays live while selection waits for a preferred language.
 * HD starts in the real player: downloading the beginning of eight files is not a playback test.
 * Only explicitly enabled UHD needs a bandwidth qualification. All jobs belong to the caller.
 */
class PlaybackSourceSession(
    private val languageGraceMs: Long = 1_200L,
    private val torrentReadyTimeoutMs: Long = 4_000L
) {
    data class Snapshot(val candidates: List<Stream> = emptyList(), val complete: Boolean = false)

    suspend fun select(
        snapshots: StateFlow<Snapshot>,
        language: String,
        qualifyUhd: suspend (List<Stream>) -> Stream?,
        prepareTorrent: suspend (List<Stream>) -> Stream?
    ): Stream? {
        val first = snapshots.first { it.candidates.isNotEmpty() || it.complete }
        if (first.candidates.isEmpty()) return null
        // A fallback arriving first must not permanently beat a preferred dub a moment later.
        // This wait observes fresh snapshots; it never blocks the provider collector.
        withTimeoutOrNull(languageGraceMs) {
            snapshots.first { snapshot ->
                snapshot.complete || snapshot.candidates.any {
                    !it.isTorrent() && MoonCatSourcePolicy.languageTier(it, language) == 0
                }
            }
        }
        val candidates = snapshots.value.candidates
        val preferred = candidates.filter { MoonCatSourcePolicy.languageTier(it, language) == 0 }
        suspend fun directWinner(streams: List<Stream>): Stream? {
            // Preserve ranking, but never accept unchecked UHD after qualification failure.
            val firstChoice = streams.firstOrNull() ?: return null
            if (!MoonCatSourcePolicy.is4k(firstChoice)) return firstChoice
            val qualified = try {
                qualifyUhd(streams.filter { MoonCatSourcePolicy.is4k(it) }.take(2))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            return qualified ?: streams.firstOrNull { !MoonCatSourcePolicy.is4k(it) }
        }
        suspend fun nativeWinner(streams: List<Stream>): Stream? = try {
            withTimeoutOrNull(torrentReadyTimeoutMs) { prepareTorrent(streams) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        directWinner(preferred.filterNot { it.isTorrent() })?.let { return it }
        val preferredTorrents = preferred.filter { it.isTorrent() }
        if (preferredTorrents.isNotEmpty()) {
            nativeWinner(preferredTorrents)?.let { return it }
        }
        // Re-read after native preparation so late direct results are eligible immediately.
        directWinner(snapshots.value.candidates.filterNot { it.isTorrent() })?.let { return it }
        val torrents = snapshots.value.candidates.filter { it.isTorrent() && it !in preferredTorrents }
        if (torrents.isNotEmpty()) {
            nativeWinner(torrents)?.let { return it }
        }
        // Native preparation is an availability hint. Let real playback handle one ranked
        // torrent if it did not become ready within the short selection budget.
        return snapshots.value.candidates.firstOrNull { !MoonCatSourcePolicy.is4k(it) }
    }
}
