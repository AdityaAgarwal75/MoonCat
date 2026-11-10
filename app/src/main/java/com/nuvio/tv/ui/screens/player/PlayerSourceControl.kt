package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nuvio.tv.R

/** Loading-only control, including torrent metadata preparation before a player exists. */
internal fun shouldShowSourceControl(state: PlayerUiState, postPlayVisible: Boolean): Boolean =
    state.error == null && !postPlayVisible && !state.showSourcesPanel && !state.showEpisodesPanel &&
        !state.showPartyPanel && !state.showAudioOverlay && !state.showSubtitleOverlay &&
        !state.showSubtitleStylePanel && !state.showSubtitleDelayOverlay && !state.showSubtitleTimingDialog &&
        !state.showSpeedDialog && !state.showMoreDialog && !state.showStreamInfoOverlay && !state.showPauseOverlay &&
        (state.showLoadingOverlay || state.isBuffering)

internal fun sourcePanelCanReturnToPlayer(state: PlayerUiState): Boolean =
    !state.currentStreamUrl.isNullOrBlank() || !state.currentStreamInfoHash.isNullOrBlank()

@Composable
internal fun SelectedSourceDetails(uiState: PlayerUiState) {
    val status = when {
        uiState.error != null -> R.string.player_source_failed
        uiState.showLoadingOverlay || uiState.isBuffering -> R.string.player_source_loading
        else -> R.string.player_source_playing
    }
    Text(stringResource(status), color = Color(0xFFFFD8A0), style = MaterialTheme.typography.labelLarge)
    uiState.currentStreamAddonName?.takeIf { it.isNotBlank() }?.let {
        Text(it, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(uiState.currentStreamName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_selected_source),
        color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    uiState.currentStreamDescription?.takeIf { it.isNotBlank() && it != uiState.currentStreamName }?.let {
        Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PlayerSourceControl(
    uiState: PlayerUiState,
    focusRequester: FocusRequester,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(280.dp).clip(RoundedCornerShape(16.dp))
        .background(Color(0xED171A20)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectedSourceDetails(uiState)
        DialogButton(text = stringResource(R.string.player_change_source), onClick = onChangeSource,
            isPrimary = true, modifier = Modifier.focusRequester(focusRequester))
        if (uiState.showLoadingOverlay || uiState.isBuffering) {
            Text(stringResource(R.string.player_source_remote_hint), color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
