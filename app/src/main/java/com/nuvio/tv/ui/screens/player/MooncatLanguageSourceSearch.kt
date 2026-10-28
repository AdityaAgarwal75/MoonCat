package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/** UI adapter only; playback work belongs to the controller and survives recomposition. */
@Composable
internal fun MooncatLanguageSourceSearch(
    language: String?,
    @Suppress("UNUSED_PARAMETER") state: PlayerUiState,
    onEvent: (PlayerEvent) -> Unit,
    onFinished: () -> Unit
) {
    val latestEvent = rememberUpdatedState(onEvent)
    val latestFinished = rememberUpdatedState(onFinished)
    LaunchedEffect(language) {
        language ?: return@LaunchedEffect
        latestEvent.value(PlayerEvent.OnRequestAudioLanguage(language))
        latestFinished.value()
    }
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
