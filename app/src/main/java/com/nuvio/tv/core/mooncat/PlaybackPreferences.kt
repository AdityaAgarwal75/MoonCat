package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.playback.AudioLanguagePolicy

/** Household playback contract. Independent of screens, networking and decoder engines. */
object PlaybackPreferences {
    const val MIN_BYTES = 1_000_000_000L
    const val MAX_BYTES = 6_000_000_000L

    fun isAnime(genres: String?, originalLanguage: String? = null, videoId: String? = null): Boolean =
        listOf("kitsu:", "mal:", "anilist:").any { videoId.orEmpty().startsWith(it, true) } ||
        genres.orEmpty().contains("anime", true) ||
            (genres.orEmpty().contains("animation", true) && AudioLanguagePolicy.normalize(originalLanguage) == "ja")

    fun eligible(stream: Stream): Boolean {
        if (stream.isExternal()) return false
        // Native server and YouTube adapters negotiate their own format on resolution.
        if (stream.serverTarget != null || stream.isYouTube()) return true
        return MoonCatSourcePolicy.qualityTier(stream) == 0 &&
            MoonCatSourcePolicy.suitable(stream, MAX_BYTES) &&
            MoonCatSourcePolicy.sizeBytes(stream)?.let { it in MIN_BYTES..MAX_BYTES } == true
    }

    fun audioLanguages(originalLanguage: String?, anime: Boolean): List<String> {
        val original = AudioLanguagePolicy.normalize(originalLanguage)
        return (if (anime) listOfNotNull("hi", "en", original)
            else listOfNotNull("hi", original)).distinct()
    }

    fun languageTier(stream: Stream, originalLanguage: String?, anime: Boolean): Int {
        val languages = audioLanguages(originalLanguage, anime)
        val hints = MoonCatSourcePolicy.languageHints(stream)
        return languages.indexOfFirst { it in hints }.takeIf { it >= 0 }
            ?: if (hints.isEmpty()) languages.size else languages.size + 1
    }

    fun order(streams: List<Stream>, originalLanguage: String?, anime: Boolean): List<Stream> =
        MoonCatSourcePolicy.rank(streams.filter(::eligible).filter { stream ->
            val allowed = audioLanguages(originalLanguage, anime)
            val hints = MoonCatSourcePolicy.languageHints(stream)
            AudioLanguagePolicy.normalize(originalLanguage) == null || hints.isEmpty() || allowed.any { it in hints }
        }).sortedWith(compareBy<Stream> { if (MoonCatSourcePolicy.availabilityTier(it) == 2) 1 else 0 }
            .thenBy { languageTier(it, originalLanguage, anime) })
}
