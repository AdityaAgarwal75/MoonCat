package com.nuvio.tv.ui.screens.player

import android.widget.Toast
import com.nuvio.tv.core.mooncat.MoonCatSourcePolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Release names are search hints only. The actual decoder tracks verify the language. */
@Composable
internal fun MooncatLanguageSourceSearch(
    language: String?,
    state: PlayerUiState,
    onEvent: (PlayerEvent) -> Unit,
    onFinished: () -> Unit
) {
    val latest = rememberUpdatedState(state)
    val context = LocalContext.current
    LaunchedEffect(language) {
        val requested = language ?: return@LaunchedEffect
        fun notice(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        onEvent(PlayerEvent.OnDismissTransientOverlay)
        onEvent(PlayerEvent.OnShowSourcesPanel)
        notice("Searching for ${languageName(requested)} audio…")
        // Allow source fetching to start, then wait a bounded period for addon responses.
        delay(600)
        var waited = 0
        while (waited < 8000 && latest.value.isLoadingSourceStreams &&
            latest.value.sourceAllStreams.none { MoonCatSourcePolicy.advertisesAudio(it, requested) }) {
            delay(250)
            waited += 250
        }
        val current = latest.value
        val originalSource = current.sourceAllStreams.firstOrNull { stream ->
            (stream.getStreamUrl() != null && stream.getStreamUrl() == current.currentStreamUrl) ||
                (current.currentStreamInfoHash != null &&
                    stream.getEffectiveInfoHash().equals(current.currentStreamInfoHash, ignoreCase = true) &&
                    stream.getEffectiveFileIdx() == current.currentStreamFileIdx)
        }
        // First use a track already exposed by the current file: no source reload.
        val activeTracks = if (current.isServerStream) current.serverAudioTracks else current.audioTracks
        AudioLanguageChoices.choices(activeTracks).firstOrNull { it.language == requested }?.let { track ->
            onEvent(PlayerEvent.OnSelectAudioTrack(track.index))
            onFinished()
            return@LaunchedEffect
        }
        val candidates = current.sourceAllStreams.filter { stream ->
            stream != originalSource && MoonCatSourcePolicy.advertisesAudio(stream, requested) &&
                MoonCatSourcePolicy.suitable(stream) && (stream.getStreamUrl() != null || stream.isTorrent())
        }.distinctBy { it.getStreamUrl() ?: "${it.getEffectiveInfoHash()}:${it.getEffectiveFileIdx()}" }
        val remaining = MoonCatSourcePolicy.rank(candidates).take(7).toMutableList()
        if (remaining.isEmpty()) {
            notice("No source advertises ${languageName(requested)} audio. You can browse Sources.")
            onFinished()
            return@LaunchedEffect
        }
        // Bad release labels must not strand the user on the first incorrect file.
        // Try at most three distinct files, then return to the original stream.
        repeat(minOf(3, remaining.size)) {
            val candidate = com.nuvio.tv.core.mooncat.MoonCatSourceRace.choose(remaining)
                ?: remaining.firstOrNull { it.isTorrent() } ?: return@repeat
            remaining.remove(candidate)
            notice("Checking ${languageName(requested)} audio…")
            onEvent(PlayerEvent.OnSourceStreamSelected(candidate))
            waited = 0
            while (waited < 8000) {
                delay(250)
                waited += 250
                val observed = latest.value
                val sourceMatches = audioSourceIdentityMatches(
                    candidateUrl = candidate.getStreamUrl(),
                    candidateHash = candidate.getEffectiveInfoHash(),
                    candidateFileIndex = candidate.getEffectiveFileIdx(),
                    observedUrl = observed.currentStreamUrl,
                    observedHash = observed.currentStreamInfoHash,
                    observedFileIndex = observed.currentStreamFileIdx,
                    originalUrl = current.currentStreamUrl
                )
                val tracks = if (observed.isServerStream) observed.serverAudioTracks else observed.audioTracks
                val track = if (sourceMatches) AudioLanguageChoices.choices(tracks)
                    .firstOrNull { it.language == requested } else null
                if (track != null) {
                    onEvent(PlayerEvent.OnSelectAudioTrack(track.index))
                    notice("${languageName(requested)} audio selected")
                    onFinished()
                    return@LaunchedEffect
                }
                // The correct file has exposed its tracks, but the promised dub is absent.
                if (sourceMatches && tracks.isNotEmpty()) break
            }
        }
        if (originalSource != null) {
            // Restore the original source after the bounded candidate budget.
            onEvent(PlayerEvent.OnSourceStreamSelected(originalSource))
            notice("No verified ${languageName(requested)} audio. Restoring your original source.")
        } else {
            notice("This source did not expose playable ${languageName(requested)} audio. Choose another source.")
        }
        onEvent(PlayerEvent.OnShowSourcesPanel)
        onFinished()
    }
}

private fun languageName(language: String) = when (language) {
    "hi" -> "Hindi"; "en" -> "English"; else -> "Japanese"
}

/** A release label alone cannot prove that a source switch has reached the requested file. */
internal fun audioSourceIdentityMatches(
    candidateUrl: String?,
    candidateHash: String?,
    candidateFileIndex: Int?,
    observedUrl: String?,
    observedHash: String?,
    observedFileIndex: Int?,
    originalUrl: String?
): Boolean = if (candidateHash != null) {
    candidateHash.equals(observedHash, ignoreCase = true) &&
        (candidateFileIndex == null || candidateFileIndex == observedFileIndex)
} else {
    candidateUrl != null && candidateUrl == observedUrl && observedUrl != originalUrl
}
