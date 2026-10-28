package com.nuvio.tv.ui.screens.player

import java.util.Locale
import com.nuvio.tv.domain.playback.AudioLanguagePolicy

/** Only embedded languages are offered; a filename is not evidence of an audio track. */
internal object AudioLanguageChoices {
    fun searchLanguage(index: Int): String? = when (index) {
        -101 -> "hi"; -102 -> "en"; -103 -> "ja"; else -> null
    }

    fun menuChoices(tracks: List<TrackInfo>): List<TrackInfo> {
        val actual = choices(tracks)
        val missing = listOf("hi" to "Hindi", "en" to "English", "ja" to "Japanese")
            .mapIndexedNotNull { index, (code, name) ->
                if (actual.any { it.language == code }) null else
                    TrackInfo(-101 - index, "$name · Search sources", code)
            }
        return actual + missing
    }

    fun languageKey(language: String?): String = AudioLanguagePolicy.normalize(language) ?: "und"

    fun trackLanguage(track: TrackInfo): String =
        AudioLanguagePolicy.language(track.language, track.name) ?: "und"

    fun choices(tracks: List<TrackInfo>): List<TrackInfo> = tracks
        .filter { it.isSupported }
        .groupBy { track -> trackLanguage(track).takeUnless { it == "und" } ?: "und:${track.index}" }
        .map { (_, variants) ->
            val language = trackLanguage(variants.first())
            val bestKey = AudioLanguagePolicy.matching(variants.map(TrackInfo::toAudioCandidate), language)
                .firstOrNull()?.key ?: variants.first().index
            val best = variants.first { it.index == bestKey }
            best.copy(language = language, name = when (language) {
                "hi" -> "Hindi"
                "en" -> "English"
                "ja" -> "Japanese"
                "und" -> "${best.name} · Track ${best.index + 1} (language unknown)"
                else -> Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
                    .takeIf { it.isNotBlank() } ?: language
            }, codec = null, channelCount = null, sampleRate = null)
        }
        .sortedBy { when (it.language) { "hi" -> "0"; "en" -> "1"; "ja" -> "2"; else -> "3${it.name}" } }

    fun selectedChoiceIndex(tracks: List<TrackInfo>, selectedIndex: Int, choices: List<TrackInfo>): Int {
        val selected = tracks.firstOrNull { it.index == selectedIndex } ?: return -1
        val selectedLanguage = trackLanguage(selected)
        return choices.firstOrNull {
            if (selectedLanguage == "und") it.index == selected.index
            else trackLanguage(it) == selectedLanguage
        }?.index ?: -1
    }
}

internal fun TrackInfo.toAudioCandidate() = AudioLanguagePolicy.Track(
    key = index, language = language, label = name, supported = isSupported,
    channels = channelCount, codec = codec, selected = isSelected
)
