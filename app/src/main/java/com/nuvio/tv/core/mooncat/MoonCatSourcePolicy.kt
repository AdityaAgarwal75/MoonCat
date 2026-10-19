package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream

/** Conservative defaults for the 1080p Fire TV Stick. Metadata is a hint, not proof of compatibility. */
object MoonCatSourcePolicy {
    const val MAX_FILE_BYTES = 7_000_000_000L
    private val excluded = Regex("(?i)(2160p|4320p|480p|360p|240p|\\b4k\\b|\\b8k\\b|remux|hdcam|camrip|telesync|telecine|screener|\\bcam\\b|\\bscr\\b)")
    private val sizePattern = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(GiB|GB|MiB|MB)\\b")
    private fun text(stream: Stream) = listOfNotNull(stream.name, stream.title, stream.description,
        stream.quality, stream.behaviorHints?.filename, stream.clientResolve?.filename).joinToString(" ")

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

    /** Advertised language hints guide selection; the player must still verify real audio tracks. */
    fun forPreferredLanguage(streams: List<Stream>, language: String): List<Stream> {
        val preferred = when (language.lowercase()) {
            "hi", "hin", "hindi" -> "hi"
            "en", "eng", "english" -> "en"
            "ja", "jpn", "japanese" -> "ja"
            else -> return streams
        }
        val advertised = streams.filter { preferred in languageHints(it) }
        // If no preferred release is advertised, try unlabelled releases rather than known other audio.
        return advertised.ifEmpty { streams.filter { languageHints(it).isEmpty() } }
    }

    private fun languageHints(stream: Stream): Set<String> {
        val description = text(stream)
        val parsed = stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty().map { it.lowercase() }
        return buildSet {
            if (Regex("(?i)(\\bhindi\\b|\\bhin\\b|🇮🇳)").containsMatchIn(description) || parsed.any { it in setOf("hi", "hin", "hindi") }) add("hi")
            if (Regex("(?i)(\\benglish\\b|\\beng\\b|🇬🇧|🇺🇸)").containsMatchIn(description) || parsed.any { it in setOf("en", "eng", "english") }) add("en")
            if (Regex("(?i)(\\bjapanese\\b|\\bjpn\\b|🇯🇵)").containsMatchIn(description) || parsed.any { it in setOf("ja", "jpn", "japanese") }) add("ja")
            if (Regex("(?i)\\b(tamil|telugu|kannada|malayalam|bengali|french|spanish|korean|chinese|russian|german)\\b").containsMatchIn(description)) add("other")
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

    fun rank(streams: List<Stream>): List<Stream> = streams.filter { suitable(it) }.sortedBy(::qualityTier)
}
