package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.player.playbackRequestKey
import com.nuvio.tv.domain.model.Stream

/** Refresh one actual request while retaining distinct CDNs and header-authorized variants. */
internal fun mergeSourceStreams(cached: List<Stream>, fresh: List<Stream>): List<Stream> {
    val merged = LinkedHashMap<Pair<String, String>, Stream>()
    (cached + fresh).forEach { stream ->
        merged[stream.addonName to stream.playbackRequestKey()] = stream
    }
    return merged.values.toList()
}
