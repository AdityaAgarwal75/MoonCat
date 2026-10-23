package com.nuvio.tv.core.player

import com.nuvio.tv.core.mooncat.MoonCatSourcePolicy
import com.nuvio.tv.domain.model.Stream

/** Bounded per-title/episode failure history. Successful playback never triggers source changes. */
class SourceFailoverSession(private val maxAttempts: Int = 4) {
    private var contentKey: String? = null
    private var attempts = 0
    private val failed = linkedSetOf<String>()

    fun begin(key: String, failedUrl: String?, failedHash: String?, fileIndex: Int?): Boolean {
        if (contentKey != key) {
            contentKey = key
            attempts = 0
            failed.clear()
        }
        failedUrl?.takeIf(String::isNotBlank)?.let { failed.add("url:$it") }
        failedHash?.let { failed.add("hash:${it.lowercase()}:${fileIndex ?: -1}") }
        if (attempts >= maxAttempts) return false
        attempts++
        return true
    }

    fun candidates(streams: List<Stream>, language: String): List<Stream> =
        MoonCatSourcePolicy.playbackCandidates(streams, language)
            .filter { stream ->
                val url = stream.getStreamUrl()
                val hash = stream.getEffectiveInfoHash()
                (url != null || hash != null || stream.serverTarget != null || stream.isYouTube() || stream.isDirectDebrid()) &&
                    (url == null || "url:$url" !in failed) &&
                    (hash == null || "hash:${hash.lowercase()}:${stream.getEffectiveFileIdx() ?: -1}" !in failed)
            }
            .distinctBy { it.getStreamUrl() ?: "${it.getEffectiveInfoHash()}:${it.getEffectiveFileIdx()}:${it.ytId}:${it.serverTarget}" }

    fun attempted(stream: Stream) {
        stream.getStreamUrl()?.let { failed.add("url:$it") }
        stream.getEffectiveInfoHash()?.let { failed.add("hash:${it.lowercase()}:${stream.getEffectiveFileIdx() ?: -1}") }
    }
}
