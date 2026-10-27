package com.nuvio.tv.ui.screens.player

import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.mooncat.MoonCatSourcePolicy
import com.nuvio.tv.core.player.playbackRequestKey
import com.nuvio.tv.core.player.audioPlaybackRequestMatches
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.playback.AudioLanguagePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** An explicit viewer request owns one bounded search, separate from passive track preferences. */
internal fun PlayerRuntimeController.cancelAudioLanguageSearch() {
    audioLanguageSearchGeneration++
    cancelOwnedAudioResolver()
    audioLanguageSearchJob?.cancel()
    audioLanguageSearchJob = null
    requestedAudioLanguageForPlayback = null
}

private fun PlayerRuntimeController.cancelOwnedAudioResolver() {
    val owned = audioLanguageResolveJob ?: return
    owned.cancel()
    if (debridResolveJob === owned) debridResolveJob = null
    audioLanguageResolveJob = null
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.requestAudioLanguage(language: String) {
    val requested = AudioLanguagePolicy.normalize(language) ?: return
    cancelAudioLanguageSearch()
    // This newer viewer intent supersedes any unresolved earlier source choice.
    debridResolveJob?.cancel()
    debridResolveJob = null
    sourceRecoveryJob?.cancel()
    sourceRecoveryJob = null
    val generation = audioLanguageSearchGeneration
    val requestKey = audioContentKey()
    val originalState = _uiState.value
    val originalHeaders = currentHeaders.toMap()
    val originalRequestKey = playbackRequestKey(
        originalState.currentStreamUrl.takeUnless { originalState.isTorrentStream },
        originalState.currentStreamInfoHash, originalState.currentStreamFileIdx, originalHeaders
    )
    val originalPosition = (_exoPlayer?.currentPosition ?: _playbackTimeline.value.currentPosition).coerceAtLeast(0L)
    val originalSource = originalState.sourceAllStreams.firstOrNull { it.matchesAudioSource(originalState, originalHeaders) }
        ?: originalState.currentStreamUrl?.takeIf { it.isNotBlank() }?.let { originalUrl ->
            Stream(name = originalState.currentStreamName, title = currentFilename, description = null,
                url = if (originalState.isTorrentStream) null else originalUrl,
                ytId = null, infoHash = originalState.currentStreamInfoHash,
                fileIdx = originalState.currentStreamFileIdx, externalUrl = null,
                behaviorHints = StreamBehaviorHints(notWebReady = null, bingeGroup = null, countryWhitelist = null,
                    proxyHeaders = ProxyHeaders(currentHeaders.toMap(), currentStreamResponseHeaders.toMap()), filename = currentFilename),
                addonName = originalState.currentStreamAddonName.orEmpty(), addonLogo = null,
                sources = currentTorrentSources)
        }
    val originalTrackPreference = rememberedTrackPreference
    val originalPersistedPreference = persistedTrackPreference
    requestedAudioLanguageForPlayback = requested
    _uiState.update { it.copy(showAudioOverlay = false) }

    audioLanguageSearchJob = scope.launch {
        fun checkSession() {
            if (generation != audioLanguageSearchGeneration || requestKey != audioContentKey()) {
                throw CancellationException("Audio request superseded")
            }
        }
        fun notice(message: String) {
            checkSession()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        var changedSource = false
        var confirmed = false
        try {
            // A just-prepared container may not have emitted its audio groups yet.
            if (_uiState.value.isBuffering && _uiState.value.activeAudioTracks().isEmpty()) {
                withTimeoutOrNull(5_000L) {
                    _uiState.first {
                        checkSession()
                        it.activeAudioTracks().isNotEmpty() || !it.isBuffering || it.error != null
                    }
                }
            }
            // Do not reload a movie or start provider work when the current file already has the dub.
            if (selectAndVerifyAudioLanguage(requested, ::checkSession)) {
                confirmed = true
                notice("${audioLanguageName(requested)} audio selected")
                return@launch
            }
            checkSession()
            if (originalState.isServerStream) {
                notice("This server did not expose playable ${audioLanguageName(requested)} audio.")
                return@launch
            }
            notice("Searching sources for ${audioLanguageName(requested)} audio…")
            loadSourceStreams(forceRefresh = false)
            val attempted = mutableSetOf<String>()
            originalSource?.let { attempted.add(it.audioSourceKey()) }
            val found = withTimeoutOrNull(65_000L) {
                repeat(3) { attempt ->
                    checkSession()
                    val next = withTimeoutOrNull(45_000L) {
                        var candidate: Stream? = null
                        _uiState.first { state ->
                            checkSession()
                            // Resolution also uses isLoadingSourceStreams; provider completion is distinct.
                            val discoveryPending = !sourceStreamsFetchCompleted && sourceStreamsJob?.isActive == true
                            candidate = withContext(Dispatchers.Default) {
                                val eligible = state.sourceAllStreams.filter { stream ->
                                    stream.audioSourceKey() !in attempted &&
                                        stream.audioSourceKey() != originalRequestKey &&
                                        MoonCatSourcePolicy.canInspectAudio(stream) &&
                                        (stream.getStreamUrl() != null || stream.isTorrent()) &&
                                        stream.externalUrl == null && stream.youTubeIdToResolve() == null
                                }
                                val advertised = eligible.filter { MoonCatSourcePolicy.advertisesAudio(it, requested) }
                                // Unknown metadata is a useful fallback only once advertised discovery finishes.
                                val fallback = if (discoveryPending) emptyList() else
                                    eligible.filter { MoonCatSourcePolicy.languageHints(it).isEmpty() }
                                MoonCatSourcePolicy.audioInspectionCandidates(advertised + fallback, requested).firstOrNull()
                            }
                            candidate != null || !discoveryPending
                        }
                        candidate
                    } ?: return@withTimeoutOrNull false
                    attempted.add(next.audioSourceKey())
                    currentCoroutineContext().ensureActive()
                    checkSession()
                    changedSource = true
                    notice("Checking ${audioLanguageName(requested)} audio (${attempt + 1}/3)…")
                    // Language intent is applied before preparation, then checked against the decoder's tracks.
                    persistedTrackPreference = null
                    rememberedTrackPreference = originalTrackPreference?.copy(audio = null)
                    switchToSourceStream(next, preserveSourceDiscovery = true, resumePositionMs = originalPosition)
                    audioLanguageResolveJob = debridResolveJob
                    val discovered = withTimeoutOrNull(9_000L) {
                        _uiState.first { observed ->
                            checkSession()
                            next.matchesAudioSource(observed, currentHeaders) &&
                                (observed.activeAudioTracks().any {
                                    AudioLanguagePolicy.language(it.language, it.name) == requested && it.isSupported
                                } || observed.error != null)
                        }
                    }
                    if (discovered != null && next.matchesAudioSource(_uiState.value, currentHeaders) &&
                        selectAndVerifyAudioLanguage(requested, ::checkSession)) {
                        return@withTimeoutOrNull true
                    }
                    // A resolving torrent/debrid task must not outlive its candidate slot.
                    cancelOwnedAudioResolver()
                }
                false
            } == true
            confirmed = found
            cancelOwnedAudioResolver()
            checkSession()
            if (found) {
                notice("${audioLanguageName(requested)} audio selected")
            } else {
                rememberedTrackPreference = originalTrackPreference
                persistedTrackPreference = originalPersistedPreference
                requestedAudioLanguageForPlayback = null
                if (changedSource && originalSource != null) {
                    switchToSourceStream(originalSource, resumePositionMs = originalPosition)
                    notice("No verified ${audioLanguageName(requested)} audio. Restoring your original source.")
                } else {
                    notice("No verified ${audioLanguageName(requested)} audio was found. You can browse Sources.")
                }
                _uiState.update { it.copy(showSourcesPanel = true) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            checkSession()
            cancelOwnedAudioResolver()
            rememberedTrackPreference = originalTrackPreference
            persistedTrackPreference = originalPersistedPreference
            requestedAudioLanguageForPlayback = null
            if (changedSource && originalSource != null) {
                switchToSourceStream(originalSource, resumePositionMs = originalPosition)
            }
            notice("Audio search failed. Your source selection is available in Sources.")
            _uiState.update { it.copy(showSourcesPanel = true) }
        } finally {
            if (generation == audioLanguageSearchGeneration) {
                cancelOwnedAudioResolver()
                requestedAudioLanguageForPlayback = null
                audioLanguageSearchJob = null
                if (!confirmed) {
                    rememberedTrackPreference = originalTrackPreference
                    persistedTrackPreference = originalPersistedPreference
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun PlayerRuntimeController.selectAndVerifyAudioLanguage(
    requested: String,
    checkSession: () -> Unit
): Boolean {
    checkSession()
    val tracks = _uiState.value.activeAudioTracks()
    val choices = AudioLanguagePolicy.matching(tracks.map(TrackInfo::toAudioCandidate), requested)
    for (choice in choices.take(3)) {
        currentCoroutineContext().ensureActive()
        checkSession()
        if (_uiState.value.isServerStream) selectServerAudio(choice.key) else selectAudioTrack(choice.key)
        val selected = withTimeoutOrNull(3_000L) {
            _uiState.first { state ->
                checkSession()
                state.activeAudioTracks().any {
                    it.index == choice.key && it.isSelected && it.isSupported &&
                        AudioLanguagePolicy.language(it.language, it.name) == requested
                }
            }
        }
        if (selected != null) {
            if (!selected.isServerStream) rememberAudioSelection(choice.key)
            return true
        }
    }
    return false
}

private fun PlayerRuntimeController.audioContentKey() = "$contentType:$currentVideoId:$currentSeason:$currentEpisode"
private fun PlayerUiState.activeAudioTracks() = if (isServerStream) serverAudioTracks else audioTracks

private fun Stream.audioSourceKey(): String {
    val url = getStreamUrl()
    if (url == null) return playbackRequestKey(null, getEffectiveInfoHash(), getEffectiveFileIdx())
    val request = PlayerMediaSourceFactory.normalizePlaybackRequest(url, behaviorHints?.proxyHeaders?.request.orEmpty())
    return playbackRequestKey(request.url, getEffectiveInfoHash(), getEffectiveFileIdx(), request.headers)
}

private fun Stream.matchesAudioSource(state: PlayerUiState, observedHeaders: Map<String, String>): Boolean {
    val request = getStreamUrl()?.let {
        PlayerMediaSourceFactory.normalizePlaybackRequest(it, behaviorHints?.proxyHeaders?.request.orEmpty())
    }
    return audioPlaybackRequestMatches(
        request?.url, getEffectiveInfoHash(), getEffectiveFileIdx(), request?.headers.orEmpty(),
        state.currentStreamUrl, state.currentStreamInfoHash, state.currentStreamFileIdx, observedHeaders
    )
}

private fun audioLanguageName(language: String): String = Locale.forLanguageTag(language)
    .getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() } ?: language
