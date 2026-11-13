package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream

/** Conservative defaults for the 1080p Fire TV Stick. Metadata is a hint, not proof of compatibility. */
object MoonCatSourcePolicy {
    const val MAX_FILE_BYTES = 100_000_000_000L
    const val USER_MAX_FILE_BYTES = PlaybackPreferences.MAX_BYTES
    fun withinUserSizeLimit(stream: Stream): Boolean =
        sizeBytes(stream)?.let { it in 1..USER_MAX_FILE_BYTES } ?: true
    fun visibleSource(stream: Stream): Boolean {
        // Manual lists include small 1080p, 720p and SD sources. Autoplay has
        // its own 1080p policy, and both enforce the 6 GB upper size limit.
        if (stream.isExternal()) return false
        if (stream.serverTarget != null || stream.isYouTube()) return true
        return suitable(stream, USER_MAX_FILE_BYTES) && withinUserSizeLimit(stream)
    }
    private val excluded = Regex("""(?i)((?:hd|hq)[ ._-]*cam|cam[ ._-]*rip|(?:hd|hq)[ ._-]*(?:ts|tc)|(?:ts|tc)[ ._-]*rip|p[ ._-]*dvd[ ._-]*rip|pre[ ._-]*dvd(?:rip)?|telesync|telecine|screener|\b(?:cam|scr|hdts|hdtc|ts|tc|predvd|pre-dvd|dvdscr|workprint)\b)""")
    private val aboveHd = Regex("""(?i)(\b(?:2160|4320|1440)p\b|3840x2160|7680x4320|\b[48][ ._-]?k\b)""")
    private val fourK = Regex("""(?i)(\b2160p\b|3840x2160|\b4[ ._-]?k\b)""")
    private val above4k = Regex("""(?i)(\b(?:4320|1440)p\b|7680x4320|\b8[ ._-]?k\b)""")
    private val sizePattern = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(GiB|GB|MiB|MB)\\b")
    private val ultraHd = Regex("""(?i)\b(?:uhd|ultra[ ._-]*hd)\b""")
    private val cleanRelease = Regex("""(?i)\b(?:web[ ._-]?dl|blu[ ._-]?ray|b[dr]rip)\b""")
    private val webRelease = Regex("""(?i)\b(?:web[ ._-]?rip|hdtv)\b""")
    private const val COUNT = "(\\d[\\d,]*(?:\\.\\d+)?[kKmM]?)\\b"
    private val seederIcon = Regex("(?:👤|👥|🌱)[\\uFE0F\\s]*$COUNT")
    private val seederLabel = Regex("(?i)\\b(?:seeders?|seeds?)\\s*[:=]?\\s*$COUNT")
    private val countBeforeLabel = Regex("(?i)$COUNT\\s+(?:seeders?|seeds?)\\b")
    private val sdResolution = Regex("""(?i)\b(?:576|540|480|360|240)p\b""")
    private val fullHdResolution = Regex("""(?i)(\b1080p\b|\b1920x1080\b)""")
    private val hdResolution = Regex("""(?i)(\b720p\b|\b1280x720\b)""")
    private fun text(stream: Stream) = listOfNotNull(stream.name, stream.title, stream.description,
        stream.quality, stream.behaviorHints?.filename, stream.clientResolve?.filename,
        stream.clientResolve?.stream?.raw?.torrentName, stream.clientResolve?.stream?.raw?.filename,
        stream.clientResolve?.stream?.raw?.parsed?.rawTitle,
        stream.clientResolve?.stream?.raw?.parsed?.resolution,
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

    fun suitable(stream: Stream, maxFileBytes: Long = MAX_FILE_BYTES, allow4k: Boolean = false): Boolean {
        val limit = maxFileBytes
        val label = text(stream)
        val isUltra = is4k(stream)
        val permitsUltra = allow4k && isUltra && !above4k.containsMatchIn(label)
        val unknownUltraHd = qualityTier(stream) == 3 && ultraHd.containsMatchIn(label)
        return stream.qualityValue <= (if (permitsUltra) 2160 else 1080) &&
            (permitsUltra || !aboveHd.containsMatchIn(label)) && !unknownUltraHd &&
            !excluded.containsMatchIn(label) && (sizeBytes(stream)?.let { it in 1..limit } ?: true)
    }

    /** Explicit language inspection caps resolution only; release names/size never hide a dub. */
    fun canInspectAudio(stream: Stream, allow4k: Boolean = false): Boolean {
        val label = text(stream)
        val exceeds4k = Regex("""(?i)(\b4320p\b|7680x4320|\b8[ ._-]?k\b)""").containsMatchIn(label)
        return if (allow4k) stream.qualityValue <= 2160 && !exceeds4k
            else stream.qualityValue <= 1080 && !aboveHd.containsMatchIn(label) && !(qualityTier(stream) == 3 && ultraHd.containsMatchIn(label))
    }

    /** Unlike autoplay ranking this retains large HD files and imperfect release descriptions. */
    fun audioInspectionCandidates(streams: List<Stream>, language: String): List<Stream> =
        streams.filter { canInspectAudio(it) && !it.isExternal() }.sortedWith(
            compareBy<Stream> { languageTier(it, language) }
                .thenBy { qualityTier(it) }.thenBy { if (it.isTorrent()) 1 else 0 }
                .thenBy { releaseTier(it) }.thenByDescending { advertisedSeeders(it) }
        )

    fun reusableRelease(name: String, filename: String?, size: Long?, preferredLanguage: String, maxFileBytes: Long): Boolean {
        val cached = Stream(name = name, title = filename, description = null, url = null, ytId = null,
            infoHash = null, fileIdx = null, externalUrl = null, behaviorHints = null, addonName = "Cached", addonLogo = null)
        return suitable(cached, maxFileBytes) && (size == null || size in 1..maxFileBytes) &&
            advertisesAudio(cached, normalizeLanguage(preferredLanguage) ?: "hi")
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

    fun normalizeLanguage(language: String): String? =
        com.nuvio.tv.domain.playback.AudioLanguagePolicy.normalize(language)

    fun advertisesAudio(stream: Stream, language: String): Boolean =
        normalizeLanguage(language)?.let { it in languageHints(stream) } == true

    fun languageHints(stream: Stream): Set<String> = buildSet {
        stream.clientResolve?.stream?.raw?.parsed?.languages.orEmpty()
            .mapNotNull(com.nuvio.tv.domain.playback.AudioLanguagePolicy::normalize).forEach(::add)
        addAll(com.nuvio.tv.domain.playback.AudioLanguagePolicy.advertisedLanguages(text(stream)))
    }

    fun is4kLabel(label: String): Boolean = fourK.containsMatchIn(label)
    fun is4k(stream: Stream): Boolean = stream.qualityValue == 2160 || is4kLabel(text(stream))

    fun qualityTier(stream: Stream): Int {
        val description = text(stream)
        return when {
            is4k(stream) -> -1
            stream.qualityValue == 1080 || fullHdResolution.containsMatchIn(description) -> 0
            stream.qualityValue == 720 || hdResolution.containsMatchIn(description) -> 1
            stream.qualityValue in 1..719 || sdResolution.containsMatchIn(description) -> 2
            else -> 3
        }
    }

    fun languageTier(stream: Stream, language: String = "hi"): Int {
        val preferred = normalizeLanguage(language) ?: "hi"
        val hints = languageHints(stream)
        return when {
            preferred in hints -> 0
            (if (preferred == "hi") "en" else "hi") in hints -> 1
            hints.isEmpty() -> 2
            else -> 3
        }
    }

    /** Language is a preference, never a requirement: retain the original-language fallback. */
    fun playbackCandidates(streams: List<Stream>, language: String = "hi", allow4k: Boolean = false): List<Stream> =
        rank(streams, language, allow4k).filter { !it.isExternal() }

    /** Keep both free HTTP hosts and torrent swarms in the bounded race, plus language fallbacks. */
    fun probePool(streams: List<Stream>, slot: Int, language: String): List<Stream> {
        val preferred = streams.filter { languageTier(it, language) == 0 }
        return when (slot) {
            0, 1 -> preferred.filter { !it.isTorrent() && it.getStreamUrl() != null }
            2, 3 -> preferred.filter { it.isTorrent() }
            6, 7 -> streams.filter { languageTier(it, language) > 0 }
            else -> preferred
        }
    }

    /** Prefer clean digital releases over unknown encodes within the same resolution. */
    fun releaseTier(stream: Stream): Int = when {
        cleanRelease.containsMatchIn(text(stream)) -> 0
        webRelease.containsMatchIn(text(stream)) -> 1
        else -> 2
    }

    /** Seed counts are availability hints, never measured bandwidth. */
    fun advertisedSeeders(stream: Stream): Int = seederCount(stream) ?: 0

    fun availabilityTier(stream: Stream): Int {
        if (!stream.isTorrent()) return 0
        val seeds = seederCount(stream)
        return if (seeds == null) 1 else if (seeds > 0) 0 else 2
    }

    private fun seederCount(stream: Stream): Int? {
        val content = text(stream)
        return sequenceOf(seederIcon, seederLabel, countBeforeLabel)
            .flatMap { it.findAll(content) }
            .mapNotNull { match ->
                val count = match.groupValues[1].replace(",", "").trim().lowercase()
                val multiplier = when (count.lastOrNull()) { 'k' -> 1_000.0; 'm' -> 1_000_000.0; else -> 1.0 }
                count.trimEnd('k', 'm').trim().toDoubleOrNull()?.let {
                    (it * multiplier).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
                }
            }.maxOrNull()
    }

    /** One ranking boundary for initial playback, episodes and recovery.
     * A known empty swarm must not beat a viable fallback merely by advertising 1080p.
     * Parse each metadata field once per candidate, not inside an O(n log n) comparator.
     */
    private data class Ranked(
        val stream: Stream, val language: Int, val availability: Int,
        val torrentSeeders: Int, val quality: Int, val seeders: Int,
        val release: Int, val size: Long
    )

    fun instantOrder(streams: List<Stream>, language: String): List<Stream> =
        ordered(streams, language, false, USER_MAX_FILE_BYTES).filter { !it.isExternal() }

    fun readinessOrder(streams: List<Stream>, language: String): List<Stream> =
        ordered(streams, language, false, MAX_FILE_BYTES)

    fun rank(streams: List<Stream>, preferredLanguage: String? = null, allow4k: Boolean = false): List<Stream> =
        ordered(streams, preferredLanguage, allow4k, MAX_FILE_BYTES)

    private fun ordered(streams: List<Stream>, language: String?, allow4k: Boolean, maxBytes: Long): List<Stream> =
        streams.asSequence().filter { suitable(it, maxBytes, allow4k) }.map { stream ->
            val seeds = seederCount(stream)
            val torrent = stream.isTorrent()
            Ranked(stream, if (language == null) 0 else languageTier(stream, language),
                if (!torrent || (seeds != null && seeds > 0)) 0 else if (seeds == null) 1 else 2,
                if (torrent) seeds ?: 0 else 0, qualityTier(stream), seeds ?: 0,
                releaseTier(stream), sizeBytes(stream) ?: Long.MAX_VALUE)
        }.sortedWith(compareBy<Ranked> { it.language }.thenBy { it.availability }
            .thenBy { if (it.quality in 0..1) 0 else it.quality }
            .thenByDescending { it.torrentSeeders }.thenBy { it.quality }
            .thenByDescending { it.seeders }.thenBy { it.release }.thenBy { it.size })
            .map { it.stream }.toList()
}
