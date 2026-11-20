package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.playback.AudioLanguagePolicy

/** Household playback contract. Independent of screens, networking and decoder engines. */
object PlaybackPreferences {
    const val MIN_BYTES = 1L
    const val MAX_BYTES = 6_000_000_000L

    fun isAnime(genres: String?, originalLanguage: String? = null, videoId: String? = null): Boolean =
        listOf("kitsu:", "mal:", "anilist:").any { videoId.orEmpty().startsWith(it, true) } ||
        genres.orEmpty().contains("anime", true) ||
            (genres.orEmpty().contains("animation", true) && AudioLanguagePolicy.normalize(originalLanguage) == "ja")

    fun eligible(stream: Stream): Boolean =
        !stream.isExternal() && MoonCatSourcePolicy.visibleSource(stream)

    fun audioLanguages(originalLanguage: String?, anime: Boolean): List<String> {
        val original = AudioLanguagePolicy.normalize(originalLanguage)
        return (if (anime) listOfNotNull("hi", "en", original)
            else listOfNotNull("hi", original)).distinct()
    }

    fun languageTier(stream: Stream, originalLanguage: String?, anime: Boolean): Int =
        MoonCatSourcePolicy.languageTier(stream, audioLanguages(originalLanguage, anime))

    /** Language hints rank available sources; they must never discard the only playable file. */
    fun order(streams: List<Stream>, originalLanguage: String?, anime: Boolean): List<Stream> =
        MoonCatSourcePolicy.rankForPlayback(streams, audioLanguages(originalLanguage, anime))
}
