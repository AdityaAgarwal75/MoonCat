package com.nuvio.tv.ui.screens.player

import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun PlayerRuntimeController.cancelSourceRecovery(resetFailures: Boolean = false) {
    sourceRecoveryGate.invalidate()
    sourceRecoveryJob?.cancel()
    sourceRecoveryJob = null
    if (resetFailures) sourceFailoverSession.reset()
}

/** Own the complete transition, including failures raised while a resolver is preparing it. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.tryAlternativeSource(detailedError: String): Boolean {
    if (audioLanguageSearchJob?.isActive == true) return false
    if (_uiState.value.isServerStream || contentType.isNullOrBlank()) return false
    if (sourceRecoveryGate.reportFailure(detailedError)) return true
    val ticket = sourceRecoveryGate.begin() ?: return true
    val requestKey = "${contentType}:${currentVideoId}:${currentSeason}:${currentEpisode}"
    val position = (_exoPlayer?.currentPosition ?: _playbackTimeline.value.currentPosition).coerceAtLeast(0L)
    fun ownsRequest() = sourceRecoveryGate.isCurrent(ticket) &&
        requestKey == "${contentType}:${currentVideoId}:${currentSeason}:${currentEpisode}"
    sourceRecoveryJob = scope.launch(start = CoroutineStart.LAZY) {
        var error = detailedError
        try {
            while (ownsRequest()) {
                if (!sourceFailoverSession.begin(requestKey, currentStreamUrl,
                        _uiState.value.currentStreamInfoHash, _uiState.value.currentStreamFileIdx,
                        currentHeaders, _uiState.value.isTorrentStream)) break
                showRecoveryOverlay()
                _uiState.update { it.copy(loadingMessage = "Trying another source…") }
                val language = playerSettingsDataStore.playerSettings.first().preferredAudioLanguage
                suspend fun candidates() = withContext(Dispatchers.Default) {
                    com.nuvio.tv.core.mooncat.PlaybackPreferences.order(
                        sourceFailoverSession.candidates(_uiState.value.sourceAllStreams, language), contentLanguage,
                        com.nuvio.tv.core.mooncat.PlaybackPreferences.isAnime(metaGenres.joinToString(), contentLanguage, currentVideoId))
                }
                var available = candidates()
                if (available.isEmpty()) {
                    loadSourceStreams(forceRefresh = true)
                    withTimeoutOrNull(8_000L) {
                        _uiState.first { state ->
                            !state.isLoadingSourceStreams || withContext(Dispatchers.Default) {
                                sourceFailoverSession.candidates(state.sourceAllStreams, language).isNotEmpty()
                            }
                        }
                    }
                    available = candidates()
                }
                ensureActive()
                if (!ownsRequest()) return@launch
                val candidate = available.firstOrNull() ?: break
                sourceFailoverSession.attempted(candidate)
                sourceRecoveryGate.takeFailure(ticket)
                switchToSourceStream(candidate, resumePositionMs = position)
                // Resolver and native preparation are sibling jobs in the controller scope.
                // Keep recovery ownership until both finish, and retain their failure events.
                val prepared = withTimeoutOrNull(12_000L) {
                    do {
                        debridResolveJob?.join()
                        torrentStreamJob?.join()
                        playbackPreparationJob?.join()
                        playerInitializationJob?.join()
                        // An engine fallback may replace the initializer while the old one exits.
                    } while (listOf(debridResolveJob, torrentStreamJob, playbackPreparationJob,
                            playerInitializationJob).any { it?.isActive == true })
                    true
                } == true
                ensureActive()
                if (!ownsRequest()) return@launch
                if (!prepared) {
                    debridResolveJob?.cancel()
                    torrentStreamJob?.cancel()
                    playbackPreparationJob?.cancel()
                    playerInitializationJob?.cancel()
                    error = "Source preparation timed out"
                    continue
                }
                val nextError = sourceRecoveryGate.takeFailure(ticket)
                    ?: _uiState.value.error ?: _uiState.value.sourceStreamsError
                if (nextError == null) return@launch
                error = nextError
            }
            if (ownsRequest()) _uiState.update { it.copy(error = error, isBuffering = false,
                showLoadingOverlay = false, loadingMessage = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (ownsRequest()) _uiState.update { it.copy(error = error, isBuffering = false,
                showLoadingOverlay = false, loadingMessage = null) }
        } finally {
            if (sourceRecoveryGate.isCurrent(ticket)) {
                sourceRecoveryGate.finish(ticket)
                sourceRecoveryJob = null
            }
        }
    }.also { it.start() }
    return true
}
