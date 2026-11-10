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

    @Test fun `source card stays hidden during playback even with controls open`() {
        val playing = loading.copy(isPlaying = true, showLoadingOverlay = false, isBuffering = false)
        assertFalse(shouldShowSourceControl(playing.copy(showControls = true), false))
        assertFalse(shouldShowSourceControl(playing.copy(showControls = false), false))
    }

    @Test fun `opening controls on a paused ready video does not show source card`() {
        val paused = loading.copy(isPlaying = false, showLoadingOverlay = false, isBuffering = false)
        assertFalse(shouldShowSourceControl(paused.copy(showControls = true), false))
        assertFalse(shouldShowSourceControl(paused.copy(showControls = false), false))
    }

    @Test fun `source card returns on rebuffering and hides again when playback resumes`() {
        val playing = loading.copy(isPlaying = true, showLoadingOverlay = false, isBuffering = false)
        assertTrue(shouldShowSourceControl(loading, false))
        assertFalse(shouldShowSourceControl(playing, false))
        assertTrue(shouldShowSourceControl(playing.copy(isBuffering = true), false))
        assertFalse(shouldShowSourceControl(playing.copy(showControls = true), false))
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
