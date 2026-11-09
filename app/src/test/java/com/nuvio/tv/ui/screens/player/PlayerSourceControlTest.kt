package com.nuvio.tv.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class PlayerSourceControlTest {
    private val loading = PlayerUiState(showControls = false, showLoadingOverlay = true,
        currentStreamInfoHash = "abc", currentStreamUrl = "")

    @Test fun `source control is available while metadata loads without a player URL`() {
        assertTrue(shouldShowSourceControl(loading, false))
        assertTrue(sourcePanelCanReturnToPlayer(loading))
    }

    @Test fun `buffering still exposes change source when loading art is disabled`() {
        assertTrue(shouldShowSourceControl(loading.copy(showLoadingOverlay = false, isBuffering = true), false))
    }

    @Test fun `playing source appears with controls and hides for unobstructed playback`() {
        val playing = loading.copy(showLoadingOverlay = false, isBuffering = false)
        assertTrue(shouldShowSourceControl(playing.copy(showControls = true), false))
        assertFalse(shouldShowSourceControl(playing, false))
    }

    @Test fun `source card does not compete for focus with panels or postplay`() {
        assertFalse(shouldShowSourceControl(loading.copy(showSourcesPanel = true), false))
        assertFalse(shouldShowSourceControl(loading.copy(showEpisodesPanel = true), false))
        assertFalse(shouldShowSourceControl(loading.copy(showAudioOverlay = true), false))
        assertFalse(shouldShowSourceControl(loading.copy(showMoreDialog = true), false))
        assertFalse(shouldShowSourceControl(loading.copy(showPauseOverlay = true), false))
        assertFalse(shouldShowSourceControl(loading, true))
        assertFalse(shouldShowSourceControl(loading.copy(error = "failed"), false))
    }

    @Test fun `empty picker exits but preparing torrent and direct sources return to player`() {
        assertFalse(sourcePanelCanReturnToPlayer(PlayerUiState()))
        assertTrue(sourcePanelCanReturnToPlayer(loading))
        assertTrue(sourcePanelCanReturnToPlayer(PlayerUiState(currentStreamUrl = "https://video.test/movie")))
    }
}
