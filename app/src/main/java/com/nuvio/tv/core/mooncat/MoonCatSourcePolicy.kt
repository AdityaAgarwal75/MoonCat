package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream

/** Conservative defaults for the 1080p Fire TV Stick. Metadata is a hint, not proof of compatibility. */
object MoonCatSourcePolicy {
    const val MAX_FILE_BYTES = 7_000_000_000L
    private val excluded = Regex("""(?i)(2160p|4320p|480p|360p|240p|\b4k\b|\b8k\b|remux|hd[ ._-]*cam|cam[ ._-]*rip|hd[ ._-]*(?:ts|tc)|pre[ ._-]*dvd(?:rip)?|telesync|telecine|screener|\b(?:cam|scr|hdts|hdtc|ts|tc|predvd|pre-dvd|dvdscr|workprint)\b)""")
    private val sizePattern = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(GiB|GB|MiB|MB)\\b")
    private fun text(stream: Stream) = listOfNotNull(stream.name, stream.title, stream.description,
        stream.quality, stream.behaviorHints?.filename, stream.clientResolve?.filename,
        stream.clientResolve?.stream?.raw?.torrentName, stream.clientResolve?.stream?.raw?.filename,
        stream.clientResolve?.stream?.raw?.parsed?.quality).joinToString(" ")

    fun sizeBytes(stream: Stream): Long? = stream.behaviorHints?.videoSize
        ?: stream.clientResolve?.stream?.raw?.size
        ?: stream.debridCacheStatus?.cachedSize
        ?: sizePattern.find(text(stream))?.let {
            val multiplier = when (it.groupValues[2].lowercase()) {
                "gib" -> 1_073_741_824.0
                "gb" -> 1_000_000_000.0
                "mib" -> 1_048_576.0
                else -> 1_000_000.0
            }
            (it.groupValues[1].toDouble() * multiplier).toLong()
        }

    fun suitable(stream: Stream, maxFileBytes: Long = MAX_FILE_BYTES): Boolean {
        val episode = stream.clientResolve?.let { it.season != null || it.episode != null || it.mediaType == "series" } == true
        val limit = if (episode) minOf(2_000_000_000L, maxFileBytes) else maxFileBytes
        return !excluded.containsMatchIn(text(stream)) && (sizeBytes(stream)?.let { it in 1..limit } ?: true)
    }

    /** Explicit audio labels outrank unknown audio; subtitles never establish a dub. */
    fun forPreferredLanguage(streams: List<Stream>, language: String): List<Stream> {
        val preferred = normalizeLanguage(language) ?: "hi"
        val advertised = streams.filter { preferred in languageHints(it) }
        if (advertised.isNotEmpty()) return advertised
        // Household fallback: English before unlabelled/original-language releases.
        if (preferred == "hi") {
            val english = streams.filter { "en" in languageHints(it) }
            if (english.isNotEmpty()) return english
        }
        if (preferred == "en") {
            val hindi = streams.filter { "hi" in languageHints(it) }
            if (hindi.isNotEmpty()) return hindi
        }
        return streams.filter { languageHints(it).isEmpty() }
    }

    fun normalizeLanguage(language: String): String? = when (language.lowercase().substringBefore('-').trim()) {
        "hi", "hin", "hindi", "हिन्दी", "हिंदी" -> "hi"
        "en", "eng", "english" -> "en"
        "ja", "jpn", "japanese" -> "ja"
        else -> null
    }

    fun advertisesAudio(stream: Stream, language: String): Boolean =
        normalizeLanguage(language)?.let { it in languageHints(stream) } == true

    fun languageHints(stream: Stream): Set<String> {
        // A provider's structured audio list and filename are stronger than subtitle flags.
        val parsed = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
        val audioText = text(stream).lineSequence().map { line ->
            line.replace(Regex("""(?i)\b(?:subtitles?|subs?|subbed|srt)\s*[:=]?\s+[^\n|;]+"""), " ")
                .replace(Regex("""(?i)\b(?:hindi|hin|english|eng|japanese|jpn)\s+(?:subtitles?|subs?|subbed)\b"""), " ")
        }.joinToString(" ")
        return buildSet {
            parsed.forEach { normalizeLanguage(it)?.let(::add) }
            if (Regex("""(?i)(\b(?:hindi|hin|hi)\b|हिन्दी|हिंदी|🇮🇳)""").containsMatchIn(audioText)) add("hi")
            if (Regex("""(?i)(\b(?:english|eng|en)\b|🇬🇧|🇺🇸)""").containsMatchIn(audioText)) add("en")
            if (Regex("""(?i)(\b(?:japanese|jpn|ja)\b|🇯🇵)""").containsMatchIn(audioText)) add("ja")
            if (Regex("""(?i)\b(tamil|telugu|kannada|malayalam|bengali|french|spanish|korean|chinese|russian|german)\b""").containsMatchIn(audioText)) add("other")
            if (parsed.isNotEmpty() && isEmpty()) add("other")
        }
    }

    fun qualityTier(stream: Stream): Int {
        val description = text(stream)
        return when {
            description.contains("1080", true) -> 0
            description.contains("720", true) -> 1
            else -> 2
        }
    }

    /** Seed counts are availability hints, never measured bandwidth. */
    fun advertisedSeeders(stream: Stream): Int {
        val content = text(stream)
        val afterIcon = Regex("""(?:👤|👥|🌱)\s*(\d[\d,]*)""").find(content)?.groupValues?.get(1)
        val named = Regex("""(?i)\b(?:seeders?|seeds?)\s*[:=]?\s*(\d[\d,]*)""").find(content)?.groupValues?.get(1)
        return (afterIcon ?: named)?.replace(",", "")?.toIntOrNull() ?: 0
    }

    fun rank(streams: List<Stream>): List<Stream> = streams.filter { suitable(it) }.sortedWith(compareBy<Stream>(::qualityTier).thenByDescending(::advertisedSeeders))
}
