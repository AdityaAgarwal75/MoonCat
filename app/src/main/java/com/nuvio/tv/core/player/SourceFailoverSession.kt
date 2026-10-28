package com.nuvio.tv.core.player

import com.nuvio.tv.core.mooncat.MoonCatSourcePolicy
import com.nuvio.tv.domain.model.Stream

/** Bounded per-title/episode failure history. Successful playback never triggers source changes. */
class SourceFailoverSession(private val maxAttempts: Int = 4) {
    private var contentKey: String? = null
    private var attempts = 0
    private val failed = linkedSetOf<String>()

    fun begin(key: String, failedUrl: String?, failedHash: String?, fileIndex: Int?, headers: Map<String, String> = emptyMap(), localTorrent: Boolean = false): Boolean {
        if (contentKey != key) {
            contentKey = key
            attempts = 0
            failed.clear()
        }
        if (!failedUrl.isNullOrBlank() || failedHash != null) {
            failed.add(playbackRequestKey(failedUrl, failedHash, fileIndex, headers))
        }
        // A local torrent engine exposes localhost HTTP to the player; that URL
        // must not hide the original failed hash/file from the attempt history.
        if (localTorrent && failedHash != null) {
            failed.add(playbackRequestKey(null, failedHash, fileIndex))
        }
        if (attempts >= maxAttempts) return false
        attempts++
        return true
    }

    fun candidates(streams: List<Stream>, language: String): List<Stream> =
        MoonCatSourcePolicy.playbackCandidates(streams, language)
            .filter { stream ->
                (stream.getStreamUrl() != null || stream.getEffectiveInfoHash() != null ||
                    stream.serverTarget != null || stream.isYouTube() || stream.isDirectDebrid()) &&
                    stream.playbackRequestKey() !in failed
            }
            .distinctBy { it.playbackRequestKey() }

    fun attempted(stream: Stream) {
        failed.add(stream.playbackRequestKey())
    }
}
