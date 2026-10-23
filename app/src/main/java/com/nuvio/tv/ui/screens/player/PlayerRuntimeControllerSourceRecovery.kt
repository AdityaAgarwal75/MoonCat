package com.nuvio.tv.ui.screens.player

import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.mooncat.MoonCatSourceRace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Called only after a real playback failure, never because an audio language is missing. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.tryAlternativeSource(detailedError: String): Boolean {
    if (_uiState.value.isServerStream || contentType.isNullOrBlank()) return false
    if (sourceRecoveryJob?.isActive == true) return true
    val requestKey = "${contentType}:${currentVideoId}:${currentSeason}:${currentEpisode}"
    if (!sourceFailoverSession.begin(requestKey, currentStreamUrl,
            _uiState.value.currentStreamInfoHash, _uiState.value.currentStreamFileIdx)) return false
    val failedUrl = currentStreamUrl
    val position = (_exoPlayer?.currentPosition ?: _playbackTimeline.value.currentPosition).coerceAtLeast(0L)
    sourceRecoveryJob = scope.launch {
        showRecoveryOverlay()
        _uiState.update { it.copy(loadingMessage = "Trying another source…") }
        try {
            val settings = playerSettingsDataStore.playerSettings.first()
            val language = settings.preferredAudioLanguage
            loadSourceStreams(forceRefresh = _uiState.value.sourceAllStreams.isEmpty())
            val candidates = withTimeoutOrNull(12_000L) {
                _uiState.first { state ->
                    withContext(Dispatchers.Default) {
                        sourceFailoverSession.candidates(state.sourceAllStreams, language).isNotEmpty()
                    }
                }.sourceAllStreams
            }?.let { streams ->
                withContext(Dispatchers.Default) { sourceFailoverSession.candidates(streams, language) }
            }.orEmpty()
            val candidate = MoonCatSourceRace.choose(candidates, language) ?: candidates.firstOrNull()
            // Never let an old failed title replace an episode or a source chosen in the meantime.
            if (failedUrl != currentStreamUrl ||
                requestKey != "${contentType}:${currentVideoId}:${currentSeason}:${currentEpisode}") return@launch
            if (candidate != null) {
                sourceFailoverSession.attempted(candidate)
                if (position > 0L) _uiState.update { it.copy(pendingSeekPosition = position) }
                // switchToSourceStream cancels discovery; it doesn't cancel this recovery job.
                switchToSourceStream(candidate)
            } else {
                _uiState.update { it.copy(error = detailedError, isBuffering = false,
                    showLoadingOverlay = false, loadingMessage = null) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _uiState.update { it.copy(error = detailedError, isBuffering = false,
                showLoadingOverlay = false, loadingMessage = null) }
        } finally {
            sourceRecoveryJob = null
        }
    }
    return true
}
