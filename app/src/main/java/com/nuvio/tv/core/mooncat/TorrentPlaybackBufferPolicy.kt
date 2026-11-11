package com.nuvio.tv.core.mooncat

import com.nuvio.tv.data.local.BufferSettings

/** Keep downloading ahead of the playhead without increasing the device byte budget. */
object TorrentPlaybackBufferPolicy {
    fun apply(buffer: BufferSettings): BufferSettings = buffer.copy(
        minBufferMs = 60_000,
        maxBufferMs = 90_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 8_000,
        backBufferDurationMs = 0
    )

    fun shouldRecover(stalledForMs: Long, renderedFirstFrame: Boolean, playWhenReady: Boolean): Boolean =
        renderedFirstFrame && playWhenReady && stalledForMs >= 20_000L
}
