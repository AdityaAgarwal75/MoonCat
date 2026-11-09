package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Bounded metadata-only selection; never opens media or starts a second torrent engine. */
internal object MetadataSourceSelector {
    const val SETTLE_MS = 250L
    const val LANGUAGE_GRACE_MS = 750L

    suspend fun select(
        snapshots: StateFlow<PlaybackSourceSession.Snapshot>,
        language: String
    ): Stream? {
        val first = snapshots.first { it.candidates.isNotEmpty() || it.complete }
        if (first.complete) return first.candidates.firstOrNull()
        // A short response window lets simultaneously queried providers contribute.
        withTimeoutOrNull(SETTLE_MS) { snapshots.first { it.complete } }
        if (!snapshots.value.complete && snapshots.value.candidates.none {
                MoonCatSourcePolicy.languageTier(it, language) == 0
            }) {
            withTimeoutOrNull(LANGUAGE_GRACE_MS - SETTLE_MS) {
                snapshots.first { it.complete || it.candidates.any { stream ->
                    MoonCatSourcePolicy.languageTier(stream, language) == 0
                } }
            }
        }
        return snapshots.value.candidates.firstOrNull()
    }
}
