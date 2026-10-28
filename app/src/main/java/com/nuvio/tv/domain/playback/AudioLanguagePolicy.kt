package com.nuvio.tv.domain.playback

import java.util.Locale

/** Container track metadata only. Release names are deliberately not accepted here. */
object AudioLanguagePolicy {
    private val aliases = mapOf(
        "hin" to "hi", "hindi" to "hi", "eng" to "en", "english" to "en",
        "jpn" to "ja", "japanese" to "ja", "tam" to "ta", "tamil" to "ta",
        "tel" to "te", "telugu" to "te", "kan" to "kn", "kannada" to "kn",
        "mal" to "ml", "malayalam" to "ml", "ben" to "bn", "bengali" to "bn",
        "mar" to "mr", "marathi" to "mr", "pan" to "pa", "punjabi" to "pa",
        "kor" to "ko", "korean" to "ko", "chi" to "zh", "zho" to "zh", "chinese" to "zh",
        "fre" to "fr", "fra" to "fr", "french" to "fr", "ger" to "de", "deu" to "de", "german" to "de",
        "spa" to "es", "spanish" to "es", "ita" to "it", "italian" to "it",
        "por" to "pt", "portuguese" to "pt", "rus" to "ru", "russian" to "ru",
        "ara" to "ar", "arabic" to "ar", "tha" to "th", "thai" to "th",
        "ind" to "id", "indonesian" to "id", "vie" to "vi", "vietnamese" to "vi"
    )
    private val isoLanguages = Locale.getISOLanguages().toSet()
    private val isoAliases = isoLanguages.mapNotNull { code ->
        runCatching { Locale.forLanguageTag(code).isO3Language to code }.getOrNull()
    }.toMap()
    private val labelTokens = Regex("[a-z]{2,}")
    private val subtitleSuffix = Regex("""(?i)\b(?:subtitles?|subs?|subbed|srt)\s*[:=]?\s+[^\n|;]+""")
    private val subtitlePrefix = Regex("""(?i)\b([a-z]+)\s+(?:subtitles?|subs?|subbed)\b""")
    private val nonLanguages = setOf("und", "unknown", "mul", "multi", "zxx", "default", "original", "auto")
    private val commentary = Regex("""(?i)\b(commentary|descriptive|description|described|narration)\b""")

    fun normalize(language: String?): String? {
        val code = language?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.substringBefore('-')
            ?.takeUnless { it.isBlank() || it in nonLanguages } ?: return null
        return aliases[code] ?: isoAliases[code] ?: code.takeIf { it in isoLanguages }
    }

    /** An explicit tag wins. A single known language in an actual track label is a safe fallback. */
    fun language(language: String?, trackLabel: String?): String? {
        normalize(language)?.let { return it }
        val named = labelTokens.findAll(trackLabel.orEmpty().lowercase(Locale.ROOT))
            .mapNotNull { aliases[it.value] }.toSet()
        return named.singleOrNull() ?: normalize(trackLabel?.trim()?.removeSurrounding("[", "]"))
    }

    /** Search hints, never verified tracks. Subtitle declarations and country flags do not prove a dub. */
    fun advertisedLanguages(releaseText: String): Set<String> {
        val audioText = releaseText.lineSequence().joinToString(" ") { line ->
            line.replace(subtitlePrefix, " ").replace(subtitleSuffix, " ")
        }
        val named = labelTokens.findAll(audioText.lowercase(Locale.ROOT)).mapNotNull { token ->
            // Two-letter words in a release title are too ambiguous to be language evidence.
            aliases[token.value] ?: isoAliases[token.value]
        }.toSet()
        val declared = Regex("""\[([^\]]+)\]""").findAll(audioText).flatMap { bracket ->
            labelTokens.findAll(bracket.groupValues[1].lowercase(Locale.ROOT)).mapNotNull { normalize(it.value) }
        }.toSet()
        return named + declared
    }

    data class Track(
        val key: Int,
        val language: String?,
        val label: String,
        val supported: Boolean = true,
        val channels: Int? = null,
        val codec: String? = null,
        val selected: Boolean = false
    )

    fun matching(tracks: List<Track>, requested: String): List<Track> {
        val target = normalize(requested) ?: return emptyList()
        return tracks.filter { it.supported && language(it.language, it.label) == target }
            .sortedWith(compareByDescending<Track> { !commentary.containsMatchIn(it.label) }
                .thenByDescending { it.channels == 2 }
                .thenByDescending { (it.channels ?: Int.MAX_VALUE) <= 6 }
                .thenByDescending { it.codec.orEmpty().contains("aac", ignoreCase = true) }
                .thenBy { it.key })
    }

    fun confirmed(tracks: List<Track>, requested: String): Boolean =
        matching(tracks, requested).any { it.selected }
}
