package com.nuvio.tv.ui.screens.player

import java.util.Locale

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

    fun languageKey(language: String?): String = when (val code = language
        ?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.substringBefore('-')) {
        "hin", "hindi" -> "hi"
        "eng", "english" -> "en"
        "jpn", "japanese" -> "ja"
        null, "", "und" -> "und"
        else -> code
    }

    fun choices(tracks: List<TrackInfo>): List<TrackInfo> = tracks
        .filter { it.isSupported }
        .groupBy { languageKey(it.language) }
        .map { (language, variants) ->
            // Avoid commentary/descriptive tracks and expensive multichannel decoding on a Stick.
            val best = variants.maxByOrNull { track ->
                val description = track.name.lowercase(Locale.ROOT)
                (if (description.contains("commentary") || description.contains("description")) -100 else 0) +
                    (if (track.channelCount == 2) 30 else if ((track.channelCount ?: 0) <= 6) 10 else 0) +
                    (if (track.codec?.lowercase(Locale.ROOT)?.contains("aac") == true) 5 else 0)
            }!!
            best.copy(language = language, name = when (language) {
                "hi" -> "Hindi"
                "en" -> "English"
                "ja" -> "Japanese"
                "und" -> "Original audio (language unknown)"
                else -> Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
                    .takeIf { it.isNotBlank() } ?: language
            }, codec = null, channelCount = null, sampleRate = null)
        }
        .sortedBy { when (it.language) { "hi" -> "0"; "en" -> "1"; "ja" -> "2"; else -> "3${it.name}" } }

    fun selectedChoiceIndex(tracks: List<TrackInfo>, selectedIndex: Int, choices: List<TrackInfo>): Int {
        val selected = tracks.firstOrNull { it.index == selectedIndex } ?: return -1
        return choices.firstOrNull { languageKey(it.language) == languageKey(selected.language) }?.index ?: -1
    }
}
