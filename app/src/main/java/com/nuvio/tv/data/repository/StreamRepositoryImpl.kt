package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.network.safeApiCall
import com.nuvio.tv.core.debrid.LocalDebridAvailabilityService
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.plugin.resolvePluginSeasonEpisode
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.DebridSettingsDataStore
import com.nuvio.tv.data.mapper.toDomain
import com.nuvio.tv.data.mediaserver.ServerStreamSource
import com.nuvio.tv.data.mediaserver.ServerStreams
import com.nuvio.tv.data.mediaserver.messageRes
import com.nuvio.tv.data.mediaserver.serverFailure
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.model.LocalScraperResult
import com.nuvio.tv.domain.model.PluginRepository
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.ScraperInfo
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.core.streams.supportsResource
import com.nuvio.tv.core.streams.supportsStreamResource
import com.nuvio.tv.core.streams.addonVideoId
import com.nuvio.tv.core.streams.addonMediaType
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject

private const val TAG = "StreamRepositoryImpl"

class StreamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AddonApi,
    private val addonRepository: AddonRepository,
    private val pluginManager: PluginManager,
    private val profileManager: ProfileManager,
    private val debridSettingsDataStore: DebridSettingsDataStore,
    private val tmdbService: TmdbService,
    private val localDebridAvailabilityService: LocalDebridAvailabilityService,
    private val serverStreams: ServerStreams
) : StreamRepository {
    private val streamSearchSessions = StreamSearchSessionCache()
    private val localPluginSearchPaused = MutableStateFlow(false)

    override fun setLocalPluginSearchPaused(paused: Boolean) {
        localPluginSearchPaused.value = paused
    }

    private enum class StreamFailureKind {
        MISSING,
        REQUEST_FAILED
    }

    private data class StreamAttemptFailure(
        val addonName: String,
        val kind: StreamFailureKind,
        val detail: String
    )

    private data class StreamSourceConfigurationSnapshot(
        val profileId: Int,
        val addons: List<Addon>,
        val pluginsEnabled: Boolean,
        val enabledScrapers: List<ScraperInfo>,
        val groupPluginsByRepository: Boolean,
        val pluginRepositories: List<PluginRepository>,
        val debridSettings: DebridSettings
    )

    override fun getStreamsFromAllAddons(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean
    ): Flow<NetworkResult<List<AddonStreams>>> = flow {
        val sourceConfiguration = captureSourceConfiguration()
        val isNativeServerRequest = serverStreams.isNativeRequest(videoId)
        val requestKey = buildStreamSearchRequestKey(sourceConfiguration, type, videoId, season, episode)

        emitAll(
            streamSearchSessions.observe(
                key = requestKey,
                forceRefresh = forceRefresh
            ) {
                fetchStreamsFromAllSources(
                    type = type,
                    videoId = videoId,
                    season = season,
                    episode = episode,
                    addons = if (isNativeServerRequest) emptyList() else sourceConfiguration.addons.filter {
                        com.nuvio.tv.core.mooncat.MoonCatStreamProviders.isAllowed(it)
                    },
                    hasCompatiblePlugins = false,
                    serverSources = if (isNativeServerRequest) serverStreams.sources(type, videoId, season, episode, forceRefresh) else emptyList()
                )
            }
        )
    }.flowOn(Dispatchers.Default)

    override suspend fun getCachedStreamsFromAllAddons(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?
    ): List<AddonStreams>? = withContext(Dispatchers.Default) {
        streamSearchSessions.peek(
            buildStreamSearchRequestKey(captureSourceConfiguration(), type, videoId, season, episode)
        )
    }

    private fun buildStreamSearchRequestKey(
        sourceConfiguration: StreamSourceConfigurationSnapshot,
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?
    ): StreamSearchRequestKey = StreamSearchRequestKey(
        profileId = sourceConfiguration.profileId,
        type = type.lowercase(),
        videoId = videoId,
        season = season,
        episode = episode,
        sourceConfiguration = buildSourceConfigurationKey(
            addons = sourceConfiguration.addons,
            pluginsEnabled = sourceConfiguration.pluginsEnabled,
            enabledScrapers = sourceConfiguration.enabledScrapers,
            groupPluginsByRepository = sourceConfiguration.groupPluginsByRepository,
            pluginRepositories = sourceConfiguration.pluginRepositories,
            debridPresentationConfiguration = sourceConfiguration.debridSettings
                .withoutRawCredentials()
                .toString(),
            serverRevision = serverStreams.revision
        )
    )

    private suspend fun captureSourceConfiguration(): StreamSourceConfigurationSnapshot {
        while (true) {
            val profileId = profileManager.activeProfileId.value
            val addons = addonRepository.getInstalledAddons().first().enabledAddons()
            val pluginsEnabled = pluginManager.pluginsEnabled.first()
            val enabledScrapers = if (pluginsEnabled) pluginManager.enabledScrapers.first() else emptyList()
            val groupPluginsByRepository = pluginsEnabled && pluginManager.groupStreamsByRepository.first()
            val pluginRepositories = if (groupPluginsByRepository) pluginManager.repositories.first() else emptyList()
            val debridSettings = debridSettingsDataStore.settings.first()

            if (profileManager.activeProfileId.value != profileId) continue

            return StreamSourceConfigurationSnapshot(
                profileId = profileId,
                addons = addons,
                pluginsEnabled = pluginsEnabled,
                enabledScrapers = enabledScrapers,
                groupPluginsByRepository = groupPluginsByRepository,
                pluginRepositories = pluginRepositories,
                debridSettings = debridSettings
            )
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun fetchStreamsFromAllSources(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        addons: List<Addon>,
        hasCompatiblePlugins: Boolean,
        serverSources: List<ServerStreamSource>
    ): Flow<NetworkResult<List<AddonStreams>>> = channelFlow {
        send(NetworkResult.Loading)

        try {
            // Filter addons that support streams for this type and id
            val requestType = addonMediaType(type, season, episode)
            val requestId = addonVideoId(type, videoId, season, episode)
            val streamAddons = addons.filter { addon ->
                addon.supportsStreamResource(requestType, requestId)
            }
            val addonSlots = Semaphore(4)
            val availabilitySlots = Semaphore(2)
            val resultMutex = Mutex()

            val attemptedAddonNames = streamAddons.map { it.displayName } + serverSources.map { it.name }
            val attemptedFailures = java.util.Collections.synchronizedList(
                mutableListOf<StreamAttemptFailure>()
            )

            // Accumulate results as they arrive
            val accumulatedResults = mutableListOf<AddonStreams>()

            coroutineScope {
                // Channel to receive results as they complete
                val resultChannel = Channel<AddonStreams>(16)
                
                // Track number of pending jobs
                val totalJobs = streamAddons.size + 1 + serverSources.size
                val completedJobs = java.util.concurrent.atomic.AtomicInteger(0)

                // Launch addon jobs
                streamAddons.forEach { addon ->
                    launch {
                        try {
                            val streamsResult = withTimeoutOrNull(12_000L) {
                                addonSlots.withPermit { getStreamsFromAddon(addon, requestType, requestId) }
                            } ?: NetworkResult.Error("Source timed out")
                            when (streamsResult) {
                                is NetworkResult.Success -> {
                                    if (streamsResult.data.isNotEmpty()) {
                                        val namedStreams = streamsResult.data.map {
                                            it.copy(addonName = addon.displayName, addonLogo = addon.logo)
                                        }
                                        resultChannel.send(
                                            AddonStreams(
                                                addonName = addon.displayName,
                                                addonLogo = addon.logo,
                                                streams = namedStreams
                                            )
                                        )
                                    } else {
                                        // Stream endpoint returned empty - try inline
                                        // streams from meta response as fallback.
                                        val inlineStreams = if (addon.supportsResource("meta", requestType, requestId)) {
                                            addonSlots.withPermit {
                                                withTimeoutOrNull(4_000L) {
                                                    fetchInlineStreamsFromMeta(addon, requestType, requestId)
                                                }.orEmpty()
                                            }
                                        } else emptyList()
                                        if (inlineStreams.isNotEmpty()) {
                                            resultChannel.send(
                                                AddonStreams(
                                                    addonName = addon.displayName,
                                                    addonLogo = addon.logo,
                                                    streams = inlineStreams
                                                )
                                            )
                                        } else {
                                            attemptedFailures += buildMissingStreamFailure(addon)
                                        }
                                    }
                                }
                                is NetworkResult.Error -> {
                                    attemptedFailures += buildAddonFailure(addon, streamsResult)
                                }
                                NetworkResult.Loading -> Unit
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e(TAG, "Addon ${addon.name} failed: ${e.message}")
                            attemptedFailures += StreamAttemptFailure(
                                addonName = addon.displayName,
                                kind = StreamFailureKind.REQUEST_FAILED,
                                detail = e.message ?: context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_request_failed)
                            )
                        } finally {
                            if (completedJobs.incrementAndGet() >= totalJobs) {
                                resultChannel.close()
                            }
                        }
                    }
                }

                serverSources.forEach { source ->
                    launch {
                        try {
                            val streams = withTimeoutOrNull(12_000L) { source.load() }
                                ?: throw java.io.IOException("Source timed out")
                            if (streams.isNotEmpty()) {
                                resultChannel.send(AddonStreams(addonName = source.name, addonLogo = null, streams = streams))
                            } else {
                                attemptedFailures += StreamAttemptFailure(
                                    addonName = source.name,
                                    kind = StreamFailureKind.MISSING,
                                    detail = context.getString(R.string.stream_error_detail_no_streams_for_id)
                                )
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            attemptedFailures += StreamAttemptFailure(
                                addonName = source.name,
                                kind = StreamFailureKind.REQUEST_FAILED,
                                detail = context.getString(e.serverFailure().messageRes())
                            )
                        } finally {
                            if (completedJobs.incrementAndGet() >= totalJobs) {
                                resultChannel.close()
                            }
                        }
                    }
                }

                launch {
                    try {
                        if (!hasCompatiblePlugins) return@launch

                        val tmdbId = withTimeoutOrNull(4_000L) { tmdbService.ensureTmdbId(videoId, type) }
                        Log.d(TAG, "Video ID: $videoId -> TMDB ID: $tmdbId (type: $type)")
                        val pluginRequest = buildPluginRequest(tmdbId, type, videoId)
                            ?: return@launch
                        val (pluginSeason, pluginEpisode) = resolvePluginSeasonEpisode(
                            videoId = videoId,
                            season = season,
                            episode = episode
                        )
                        withTimeoutOrNull(45_000L) {
                            localPluginSearchPaused
                            .transformLatest { paused ->
                                if (!paused) {
                                    streamLocalPlugins(
                                        pluginId = pluginRequest.id,
                                        mediaType = pluginRequest.mediaType,
                                        pluginSource = pluginRequest.source,
                                        season = pluginSeason,
                                        episode = pluginEpisode,
                                        resultChannel = resultChannel
                                    )
                                    emit(Unit)
                                }
                            }
                            .first()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Plugin execution failed: ${e.message}")
                    } finally {
                        if (completedJobs.incrementAndGet() >= totalJobs) {
                            resultChannel.close()
                        }
                    }
                }

                // Publish discovery immediately. Device/language ranking belongs to selection,
                // not discovery: filtering here made existing dubbed files undiscoverable.
                // Debrid availability is optional enrichment and cannot hold up another provider.
                for (rawResult in resultChannel) {
                    val result = rawResult.copy(streams = rawResult.streams.filter {
                        com.nuvio.tv.core.mooncat.MoonCatSourcePolicy.withinUserSizeLimit(it)
                    })
                    resultMutex.withLock {
                        mergeDiscoveryResult(accumulatedResults, result)
                        send(NetworkResult.Success(accumulatedResults.toList()))
                    }
                    launch {
                        val checkedResult = try {
                            withTimeoutOrNull(2_500L) {
                                availabilitySlots.withPermit {
                                    localDebridAvailabilityService.annotateCachedAvailability(listOf(result))
                                        .firstOrNull()
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            null // Optional provider availability never destroys playable results.
                        }
                        if (checkedResult != null && checkedResult != result) {
                            val limitedResult = checkedResult.copy(streams = checkedResult.streams.filter {
                                com.nuvio.tv.core.mooncat.MoonCatSourcePolicy.withinUserSizeLimit(it)
                            })
                            resultMutex.withLock {
                                mergeDiscoveryResult(accumulatedResults, limitedResult)
                                send(NetworkResult.Success(accumulatedResults.toList()))
                            }
                        }
                    }
                }
            }

            // Emit final result (even if empty)
            if (accumulatedResults.isEmpty()) {
                val errorMessage = buildAggregateFailureMessage(
                    type = type,
                    id = videoId,
                    attemptedAddonNames = attemptedAddonNames,
                    failures = attemptedFailures.toList()
                )
                if (errorMessage != null) {
                    send(NetworkResult.Error(errorMessage))
                } else {
                    send(NetworkResult.Success(emptyList()))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to fetch streams: ${e.message}", e)
            send(NetworkResult.Error(e.message ?: context.getString(com.nuvio.tv.R.string.stream_error_fetch_failed)))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSourceConfigurationKey(
        addons: List<Addon>,
        pluginsEnabled: Boolean,
        enabledScrapers: List<ScraperInfo>,
        groupPluginsByRepository: Boolean,
        pluginRepositories: List<PluginRepository>,
        debridPresentationConfiguration: String,
        serverRevision: Int
    ): String = buildString {
        append("addons:")
        addons.forEach { addon ->
            append("|addon:").append(addon)
        }
        append("plugins:").append(pluginsEnabled)
        append("|grouped:").append(groupPluginsByRepository)
        enabledScrapers.forEach { scraper ->
            append("|scraper:").append(scraper)
        }
        if (groupPluginsByRepository) {
            pluginRepositories.forEach { repository ->
                append("|repo:").append(repository)
            }
        }
        append("|debrid:").append(debridPresentationConfiguration)
        append("|servers:").append(serverRevision)
    }.sha256()

    private fun DebridSettings.withoutRawCredentials(): DebridSettings = copy(
        torboxApiKey = torboxApiKey.sha256(),
        premiumizeApiKey = premiumizeApiKey.sha256(),
        realDebridApiKey = realDebridApiKey.sha256()
    )

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class PluginRequest(
        val id: String,
        val mediaType: String,
        val source: String
    )

    private fun buildPluginRequest(tmdbId: String?, type: String, videoId: String): PluginRequest? {
        if (tmdbId != null) {
            return PluginRequest(
                id = tmdbId,
                mediaType = normalizeTmdbPluginType(type),
                source = "TMDB"
            )
        }

        if (!videoId.canRunLocalPlugins()) return null

        return PluginRequest(
            id = if (videoId.startsWith("kitsu:", ignoreCase = true)) {
                cleanKitsuPluginId(videoId)
            } else {
                videoId
            },
            mediaType = type.lowercase(),
            source = videoId.substringBefore(":").uppercase()
        )
    }

    private fun normalizeTmdbPluginType(type: String): String {
        return when (type.lowercase()) {
            "series", "tv", "show" -> "tv"
            else -> type.lowercase()
        }
    }

    private fun cleanKitsuPluginId(videoId: String): String {
        val parts = videoId.split(":")
        return if (parts.size > 2 && parts.last().toIntOrNull() != null) {
            parts.dropLast(1).joinToString(":")
        } else {
            videoId
        }
    }

    private fun mergeDiscoveryResult(
        accumulatedResults: MutableList<AddonStreams>,
        result: AddonStreams
    ) {
        val existingIndex = accumulatedResults.indexOfFirst { it.addonName == result.addonName }
        if (existingIndex >= 0) {
            val existing = accumulatedResults[existingIndex]
            accumulatedResults[existingIndex] = existing.copy(
                streams = mergeStreams(existing.streams, result.streams)
            )
        } else {
            accumulatedResults.add(result.copy(streams = mergeStreams(emptyList(), result.streams)))
        }
    }

    private fun mergeStreams(existing: List<Stream>, incoming: List<Stream>): List<Stream> {
        val streamsByKey = LinkedHashMap<StreamDiscoveryIdentity, Stream>()
        existing.forEach { stream -> streamsByKey[stream.discoveryIdentity()] = stream }
        incoming.forEach { stream -> streamsByKey[stream.discoveryIdentity()] = stream }
        return streamsByKey.values.toList()
    }

    /**
     * Stream local plugin results - each scraper sends results individually
     */
    private fun String.canRunLocalPlugins(): Boolean {
        return startsWith("kitsu:", ignoreCase = true) ||
            startsWith("anilist:", ignoreCase = true) ||
            startsWith("mal:", ignoreCase = true)
    }

    private suspend fun streamLocalPlugins(
        pluginId: String,
        mediaType: String,
        pluginSource: String,
        season: Int?,
        episode: Int?,
        resultChannel: Channel<AddonStreams>
    ) {
        // Check if plugins are enabled
        if (!pluginManager.pluginsEnabled.first()) {
            Log.d(TAG, "Plugins are disabled")
            return
        }

        Log.d(TAG, "Streaming plugins for $pluginSource: $pluginId, type: $mediaType")

        try {
            val groupByRepository = pluginManager.groupStreamsByRepository.first()
            val repositoriesById = if (groupByRepository) {
                pluginManager.repositories.first().associateBy { it.id }
            } else {
                emptyMap()
            }

            // Collect streaming results from each scraper
            pluginManager.executeScrapersStreaming(
                tmdbId = pluginId,
                mediaType = mediaType,
                season = season,
                episode = episode
            ).collect { (scraper, results) ->
                if (results.isNotEmpty()) {
                    val addonName = scraper.pluginAddonName(groupByRepository, repositoriesById)
                    val addonStreams = AddonStreams(
                        addonName = addonName,
                        addonLogo = null,
                        streams = results.map { result -> result.toPluginStream(scraper, addonName) }
                    )
                    resultChannel.send(addonStreams)
                    Log.d(TAG, "Streamed ${results.size} results from ${scraper.name}")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to stream plugins: ${e.message}", e)
        }
    }

    private fun ScraperInfo.pluginAddonName(
        groupByRepository: Boolean,
        repositoriesById: Map<String, PluginRepository>
    ): String {
        if (!groupByRepository) return name
        return repositoriesById[repositoryId]?.name?.takeIf { it.isNotBlank() } ?: name
    }

    private fun LocalScraperResult.toPluginStream(scraper: ScraperInfo, addonName: String): Stream {
        val baseTitle = title.takeIf { it.isNotBlank() }
        val baseName = name?.takeIf { it.isNotBlank() }
        val quality = quality?.takeIf { it.isNotBlank() }
        val qualityLabel = quality ?: context.getString(com.nuvio.tv.R.string.stream_quality_unknown)
        val displayName = buildString {
            append(baseName ?: baseTitle ?: scraper.name)
            if (!toString().contains(qualityLabel)) {
                append(" - ").append(qualityLabel)
            }
        }.takeIf { it.isNotBlank() }
        val displayTitle = (baseTitle ?: baseName ?: scraper.name).takeIf { it.isNotBlank() }

        return Stream(
            name = displayName,
            title = displayTitle,
            url = url,
            addonName = addonName,
            addonLogo = null,
            description = buildDescription(this),
            behaviorHints = if (headers != null || audioDelayMs != null) {
                StreamBehaviorHints(
                    notWebReady = null,
                    bingeGroup = null,
                    countryWhitelist = null,
                    proxyHeaders = headers?.let { ProxyHeaders(request = it, response = null) },
                    audioDelayMs = audioDelayMs
                )
            } else {
                null
            },
            infoHash = infoHash,
            fileIdx = null,
            ytId = null,
            externalUrl = null,
            quality = quality,
            qualityValue = parseQualityValue(quality),
            subtitles = subtitles
        )
    }


    /**
     * Build a description string from scraper result
     */
    private fun buildDescription(result: com.nuvio.tv.domain.model.LocalScraperResult): String? {
        // Quality is shown in the stream name — only show size/language in description
        val parts = mutableListOf<String>()
        result.size?.let { parts.add(it) }
        result.language?.let { parts.add(it) }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    private fun parseQualityValue(quality: String?): Int {
        if (quality == null) return -1
        val lower = quality.lowercase()
        return when {
            lower.contains("4k") || lower.contains("2160") -> 2160
            lower.contains("1080") -> 1080
            lower.contains("800") -> 800
            lower.contains("720") -> 720
            lower.contains("480") -> 480
            lower.contains("360") -> 360
            else -> -1
        }
    }

    override suspend fun getStreamsFromAddon(
        addon: Addon,
        type: String,
        videoId: String
    ): NetworkResult<List<Stream>> {
        val cleanBaseUrl = addon.baseUrl.trimEnd('/')
        val queryStart = cleanBaseUrl.indexOf('?')
        val basePath = if (queryStart >= 0) cleanBaseUrl.substring(0, queryStart).trimEnd('/') else cleanBaseUrl
        val baseQuery = if (queryStart >= 0) cleanBaseUrl.substring(queryStart) else ""
        val encodedType = encodePathSegment(type)
        val encodedVideoId = encodePathSegment(videoId)
        val streamUrl = "$basePath/stream/$encodedType/$encodedVideoId.json$baseQuery"
        Log.d(TAG, "Fetching streams type=$type videoId=$videoId ")

        // Display info comes from the installed addon the caller already holds. Calling
        // addonRepository.fetchAddon() here caused an unconditional manifest GET ahead of every
        // queried addon's stream request: fetchAddon is the low-level fetch that does not consult
        // the cache, so this sidestepped the manifest-cache policy in AddonRepositoryImpl.
        val addonName = addon.displayName
        val addonLogo = addon.logo

        return when (val result = safeApiCall(context) { api.getStreams(streamUrl) }) {
            is NetworkResult.Success -> {
                val streams = result.data.streams?.map { 
                    it.toDomain(addonName, addonLogo) 
                } ?: emptyList()
                Log.d(TAG, "Streams success addon=$addonName count=${streams.size} ")
                NetworkResult.Success(streams)
            }
            is NetworkResult.Error -> {
                Log.w(
                    TAG,
                    "Streams failed addon=$addonName code=${result.code} message=${result.message} "
                )
                result
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    /**
     * Fetch meta for the given content and extract inline streams from the
     * matching video entry.  Returns an empty list when the addon doesn't
     * support meta or the video has no inline streams.
     */
    private suspend fun fetchInlineStreamsFromMeta(
        addon: Addon,
        type: String,
        videoId: String
    ): List<Stream> {
        // For inline streams the meta is fetched using the content-level ID
        // (everything before the video-specific suffix).  For "other" type
        // the videoId IS the content ID; for series it is contentId:S:E.
        // Video ID formats:
        //   tt1234567:1:5      → metaId = tt1234567
        //   mal:63375:1:5      → metaId = mal:63375
        //   kitsu:12345:2      → metaId = kitsu:12345
        // Strategy: drop up to 2 trailing numeric segments (season, episode)
        // but never reduce below 2 segments for prefixed IDs (mal:X, kitsu:X).
        val metaId = run {
            val parts = videoId.split(":")
            if (parts.size <= 1) return@run videoId
            // Count trailing numeric segments
            val trailingNumericCount = parts.reversed().takeWhile { it.toIntOrNull() != null }.size
            // Keep at least 2 segments for prefixed IDs (e.g. "mal:63375"),
            // or 1 segment for IMDB-style IDs (e.g. "tt1234567")
            val firstSegment = parts.first()
            val minSegments = if (firstSegment.startsWith("tt") || firstSegment.toIntOrNull() != null) 1 else 2
            val segmentsToDrop = trailingNumericCount.coerceAtMost((parts.size - minSegments).coerceAtLeast(0))
            if (segmentsToDrop > 0) {
                parts.dropLast(segmentsToDrop).joinToString(":")
            } else {
                videoId
            }
        }
        val cleanBaseUrl = addon.baseUrl.trimEnd('/')
        val queryStart = cleanBaseUrl.indexOf('?')
        val basePath = if (queryStart >= 0) cleanBaseUrl.substring(0, queryStart).trimEnd('/') else cleanBaseUrl
        val baseQuery = if (queryStart >= 0) cleanBaseUrl.substring(queryStart) else ""
        val encodedType = encodePathSegment(type)
        val encodedMetaId = encodePathSegment(metaId)
        val metaUrl = "$basePath/meta/$encodedType/$encodedMetaId.json$baseQuery"
        Log.d(TAG, "Fetching inline streams via meta type=$type metaId=$metaId videoId=$videoId ")
        return try {
            when (val result = safeApiCall(context) { api.getMeta(metaUrl) }) {
                is NetworkResult.Success -> {
                    val metaDto = result.data.meta ?: return emptyList()
                    val matchingVideo = metaDto.videos?.firstOrNull { it.id == videoId }
                    val streams = matchingVideo?.streams
                        ?.mapNotNull { it.toDomain(addon.displayName, addon.logo) }
                        ?: emptyList()
                    Log.d(TAG, "Inline streams from meta: addon=${addon.displayName} videoId=$videoId found=${streams.size}")
                    streams
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to fetch inline streams from meta for ${addon.displayName}: ${e.message}")
            emptyList()
        }
    }

    private fun buildMissingStreamFailure(addon: Addon): StreamAttemptFailure {
        return StreamAttemptFailure(
            addonName = addon.displayName,
            kind = StreamFailureKind.MISSING,
            detail = context.getString(com.nuvio.tv.R.string.stream_error_detail_no_streams_for_id)
        )
    }

    private fun buildAddonFailure(addon: Addon, error: NetworkResult.Error): StreamAttemptFailure {
        if (error.code == 404 || error.message.equals("Not Found", ignoreCase = true)) {
            return buildMissingStreamFailure(addon)
        }
        val normalizedReason = when {
            error.message.contains("Unable to resolve host", ignoreCase = true) ->
                context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_unreachable)
            error.message.contains("Failed to connect", ignoreCase = true) ->
                context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_connection_failed)
            error.message.contains("timeout", ignoreCase = true) ->
                context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_timeout)
            error.message.contains("CLEARTEXT communication", ignoreCase = true) ->
                context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_cleartext_blocked)
            error.message.isBlank() ->
                context.getString(com.nuvio.tv.R.string.stream_error_detail_addon_request_failed)
            else -> error.message.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
        val httpSuffix = error.code?.let { " (HTTP $it)" } ?: ""
        return StreamAttemptFailure(
            addonName = addon.displayName,
            kind = StreamFailureKind.REQUEST_FAILED,
            detail = "$normalizedReason$httpSuffix"
        )
    }

    private fun buildAggregateFailureMessage(
        type: String,
        id: String,
        attemptedAddonNames: List<String>,
        failures: List<StreamAttemptFailure>
    ): String? {
        if (attemptedAddonNames.isEmpty()) {
            return context.getString(R.string.error_stream_no_supported_addon, type)
        }

        val triedAddons = attemptedAddonNames.joinToString(", ")
        val missingOnly = failures.isNotEmpty() && failures.all { it.kind == StreamFailureKind.MISSING }
        if (failures.isEmpty() || missingOnly) {
            return context.getString(R.string.error_stream_tried_none, triedAddons, id, type)
        }

        val issueSummary = failures
            .filter { it.kind == StreamFailureKind.REQUEST_FAILED }
            .distinctBy { it.addonName to it.detail }
            .take(3)
            .joinToString("; ") { "${it.addonName}: ${it.detail}" }

        return if (issueSummary.isBlank()) {
            context.getString(R.string.error_stream_tried_generic, triedAddons, id, type)
        } else {
            context.getString(R.string.error_stream_tried_issues, triedAddons, id, type, issueSummary)
        }
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
