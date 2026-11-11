package com.nuvio.tv.core.mooncat

import com.nuvio.tv.data.local.BufferSettings
import org.junit.Assert.*
import org.junit.Test

class TorrentPlaybackBufferPolicyTest {
    @Test fun `torrent keeps forward headroom without raising heap budget`() {
        val saved = BufferSettings(targetBufferSizeMb = 25, backBufferDurationMs = 15_000)
        val tuned = TorrentPlaybackBufferPolicy.apply(saved)
        assertEquals(25, tuned.targetBufferSizeMb)
        assertEquals(0, tuned.backBufferDurationMs)
        assertEquals(1_000, tuned.bufferForPlaybackMs)
        assertEquals(8_000, tuned.bufferForPlaybackAfterRebufferMs)
        assertEquals(60_000, tuned.minBufferMs)
        assertEquals(90_000, tuned.maxBufferMs)
        assertEquals(15_000, saved.backBufferDurationMs)
    }
    @Test fun `only prolonged post start stalls recover and paused playback never switches`() {
        assertFalse(TorrentPlaybackBufferPolicy.shouldRecover(19_999, true, true))
        assertTrue(TorrentPlaybackBufferPolicy.shouldRecover(20_000, true, true))
        assertFalse(TorrentPlaybackBufferPolicy.shouldRecover(60_000, false, true))
        assertFalse(TorrentPlaybackBufferPolicy.shouldRecover(60_000, true, false))
    }
}
