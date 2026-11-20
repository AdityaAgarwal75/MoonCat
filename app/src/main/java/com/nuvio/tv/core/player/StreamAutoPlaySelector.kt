package com.nuvio.tv.core.player

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.mooncat.MoonCatSourcePolicy
import com.nuvio.tv.core.mooncat.PlaybackPreferences
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

object StreamAutoPlaySelector {
    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>,
        preferredNames: Set<String> = emptySet()
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams
        if (preferredNames.isNotEmpty()) {
            val (preferred, rest) = streams.partition { it.addonName in preferredNames }
            return preferred + orderAddonStreams(rest, installedOrder)
        }

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = streams.partition {
            it.streams.any { stream -> stream.isDirectDebrid() }
        }
        if (installedOrder.isEmpty()) return directDebridEntries + remainingEntries
        val (addonEntries, pluginEntries) = remainingEntries.partition { it.addonName in addonRankByName }
        val orderedAddons = addonEntries.sortedBy { addonRankByName.getValue(it.addonName) }
        return directDebridEntries + orderedAddons + pluginEntries
    }

    internal fun hasPlayableRoute(stream: Stream): Boolean {
        // External URL streams (e.g. error pages, web links) are not playable.
        if (stream.isExternal()) return false
        if (stream.serverTarget != null) return true
        // Streams with a direct URL are always playable regardless of debrid cache status.
        if (stream.getStreamUrl() != null) return true
        // Uncached on a debrid service does not mean unavailable to the local
        // torrent engine. Readiness is verified separately by its bounded race.
        if (stream.isTorrent()) return stream.getEffectiveInfoHash() != null
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.isDirectDebrid() ||
            // A YouTube id is resolved to a playable URL on the device when the stream is played.
            // Builds without in-app YouTube playback open the watch page instead, an external link.
            (AppFeaturePolicy.inAppTrailerPlaybackEnabled && stream.youTubeIdToResolve() != null)
    }



    internal fun selectEpisodeFilenameMatch(
        streams: List<Stream>,
        filename: String,
        originalLanguage: String?,
        anime: Boolean,
        requiredBingeGroup: String? = null
    ): Stream? {
        val ranked = PlaybackPreferences.order(streams.filter(::hasPlayableRoute), originalLanguage, anime)
        val first = ranked.firstOrNull() ?: return null
        val bestLanguage = PlaybackPreferences.languageTier(first, originalLanguage, anime)
        val hasViableSource = MoonCatSourcePolicy.availabilityTier(first) != 2
        return ranked.firstOrNull { stream ->
            (requiredBingeGroup == null || stream.behaviorHints?.bingeGroup == requiredBingeGroup) &&
                FilenameEpisodeMatcher.matches(stream.behaviorHints?.filename, filename) &&
                (!hasViableSource || MoonCatSourcePolicy.availabilityTier(stream) != 2) &&
                PlaybackPreferences.languageTier(stream, originalLanguage, anime) == bestLanguage
        }
    }

    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        preferredAudioLanguage: String? = null,
        allow4k: Boolean = false,
        originalLanguage: String? = null,
        anime: Boolean = false,
        usePlaybackPreferences: Boolean = false
    ): Stream? {
        return selectAutoPlayCandidates(streams, mode, regexPattern, source, installedAddonNames, selectedAddons, selectedPlugins, preferredBingeGroup, preferBingeGroupInSelection, bingeGroupOnly, preferredAudioLanguage, allow4k, originalLanguage, anime, usePlaybackPreferences).firstOrNull()
    }

    fun selectAutoPlayCandidates(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        preferredAudioLanguage: String? = null,
        allow4k: Boolean = false,
        originalLanguage: String? = null,
        anime: Boolean = false,
        usePlaybackPreferences: Boolean = false
    ): List<Stream> {
        if (streams.isEmpty()) return emptyList()

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

        val sourceScopedStreams = when (effectiveSource) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.serverTarget != null || it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        fun rank(values: List<Stream>) = if (usePlaybackPreferences)
            PlaybackPreferences.order(values, originalLanguage, anime)
            else MoonCatSourcePolicy.rank(values, preferredAudioLanguage, allow4k)
        fun languageTier(stream: Stream) = if (usePlaybackPreferences)
            PlaybackPreferences.languageTier(stream, originalLanguage, anime)
            else MoonCatSourcePolicy.languageTier(stream, preferredAudioLanguage ?: "hi")
        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        val canReuseBingeGroup = preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()
        if (!canReuseBingeGroup && (bingeGroupOnly || mode == StreamAutoPlayMode.MANUAL)) return emptyList()

        val candidateStreams = rank(sourceScopedStreams.filter { stream ->
            hasPlayableRoute(stream) && when {
                stream.serverTarget != null -> true
                stream.addonName in installedAddonNames ->
                    selectedAddons.isEmpty() || stream.addonName in selectedAddons
                else -> selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        })

        // Reuse checks all providers, even when autoplay is limited to selected providers.
        if (canReuseBingeGroup) {
            val bestLanguage = if (preferredAudioLanguage == null) null
                else if (usePlaybackPreferences) candidateStreams.firstOrNull()?.let(::languageTier)
                else candidateStreams.minOfOrNull(::languageTier)
            val hasViableCandidate = usePlaybackPreferences &&
                candidateStreams.firstOrNull()?.let { MoonCatSourcePolicy.availabilityTier(it) != 2 } == true
            val bingeGroupMatch = rank(streams.filter { stream ->
                stream.behaviorHints?.bingeGroup == targetBingeGroup &&
                    hasPlayableRoute(stream) &&
                    (bestLanguage == null || languageTier(stream) == bestLanguage) &&
                    (!hasViableCandidate || MoonCatSourcePolicy.availabilityTier(stream) != 2)
            }).firstOrNull()
            if (bingeGroupMatch != null) {
                return if (mode == StreamAutoPlayMode.MANUAL || bingeGroupOnly) listOf(bingeGroupMatch)
                    else listOf(bingeGroupMatch) + candidateStreams.filter { it != bingeGroupMatch }
            }
        }

        if (bingeGroupOnly || candidateStreams.isEmpty()) return emptyList()

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> emptyList()
            StreamAutoPlayMode.FIRST_STREAM -> {
                if (usePlaybackPreferences) return candidateStreams
                val (ready, pending) = candidateStreams.partition {
                    !it.isTorrent() || it.debridCacheStatus == null ||
                        it.debridCacheStatus.state == StreamDebridCacheState.CACHED
                }
                ready + pending
            }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val userRegex = runCatching { Regex(regexPattern.trim(), RegexOption.IGNORE_CASE) }
                    .getOrNull() ?: return emptyList()

                candidateStreams.filter { stream ->
                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(stream.getStreamUrl().orEmpty())
                        if (stream.isTorrent()) append(' ').append(stream.infoHash.orEmpty())
                    }

                    userRegex.containsMatchIn(searchableText)
                }
            }

        }
    }
}
