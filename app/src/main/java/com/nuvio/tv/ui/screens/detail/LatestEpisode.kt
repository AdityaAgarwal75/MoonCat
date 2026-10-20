package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.core.util.isEpisodeReleaseAired
import com.nuvio.tv.core.util.parseEpisodeReleaseInstant
import com.nuvio.tv.domain.model.Video
import java.time.Clock

/** Unknown dates are not evidence that an episode has aired. Specials do not displace the series. */
internal fun latestReleasedEpisode(videos: List<Video>, clock: Clock = Clock.systemDefaultZone()): Video? =
    videos.asSequence().filter {
        it.available != false && (it.season ?: 0) > 0 && (it.episode ?: 0) > 0 &&
            isEpisodeReleaseAired(it.released, clock) == true
    }.maxWithOrNull(compareBy<Video> { parseEpisodeReleaseInstant(it.released, clock.zone) }
        .thenBy { it.season }.thenBy { it.episode })
