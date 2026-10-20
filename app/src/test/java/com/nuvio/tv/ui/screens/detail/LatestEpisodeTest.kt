package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.Video
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class LatestEpisodeTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private fun episode(id: String, released: String?, season: Int = 1, available: Boolean? = true) =
        Video(id, id, released, null, season = season, episode = 1, overview = null, available = available)

    @Test fun `latest excludes future unknown unavailable and specials`() {
        val latest = episode("latest", "2026-10-19T20:00:00Z")
        assertEquals(latest, latestReleasedEpisode(listOf(
            episode("old", "2026-10-01"), latest,
            episode("future", "2026-10-21"), episode("unknown", null),
            episode("special", "2026-10-20", season = 0),
            episode("unavailable", "2026-10-20", available = false)
        ), clock))
    }

    @Test fun `timestamp later today remains unaired`() {
        assertNull(latestReleasedEpisode(listOf(episode("later", "2026-10-20T23:00:00Z")), clock))
    }

    @Test fun `series without confirmed released episodes has no latest action`() {
        assertNull(latestReleasedEpisode(emptyList(), clock))
        assertNull(latestReleasedEpisode(listOf(episode("unknown", "invalid")), clock))
    }
}
