package com.nuvio.tv.core.mooncat

import com.nuvio.tv.data.local.BufferSettings
import com.nuvio.tv.data.local.PlayerSettings
import org.junit.Assert.*
import org.junit.Test

class FireTvPlaybackPolicyTest {
    private val mb = 1024L * 1024

    @Test fun `one GB stick ignores unsafe saved options without changing language`() {
        val saved = PlayerSettings(allow4kWhenFast = true, parallelNetworkEnabled = true,
            nuvioPerformanceModeEnabled = true, bufferBudgetManaged = false,
            allowLargeTargetBuffer = true, preferredAudioLanguage = "hi",
            bufferSettings = BufferSettings(backBufferDurationMs = 60_000))
        val effective = FireTvPlaybackPolicy.apply(saved, 900 * mb, 256 * mb)
        assertFalse(effective.allow4kWhenFast)
        assertFalse(effective.parallelNetworkEnabled)
        assertFalse(effective.nuvioPerformanceModeEnabled)
        assertTrue(effective.bufferBudgetManaged)
        assertFalse(effective.allowLargeTargetBuffer)
        assertEquals(0, effective.bufferSettings.backBufferDurationMs)
        assertEquals("hi", effective.preferredAudioLanguage)
        assertTrue(saved.parallelNetworkEnabled)
    }

    @Test fun `small heaps and unknown devices are treated conservatively`() {
        assertTrue(FireTvPlaybackPolicy.constrained(4_000 * mb, 128 * mb))
        assertTrue(FireTvPlaybackPolicy.constrained(0, 512 * mb))
        assertEquals(25, FireTvPlaybackPolicy.heapBufferMb(128 * mb))
        assertEquals(48, FireTvPlaybackPolicy.heapBufferMb(256 * mb))
    }

    @Test fun `invalid persisted durations cannot violate load control constraints`() {
        val effective = FireTvPlaybackPolicy.apply(PlayerSettings(bufferSettings = BufferSettings(
            minBufferMs = -1, maxBufferMs = -100, bufferForPlaybackMs = Int.MAX_VALUE,
            bufferForPlaybackAfterRebufferMs = Int.MAX_VALUE, backBufferDurationMs = -1
        )), 4_000 * mb, 1_024 * mb).bufferSettings
        assertTrue(effective.minBufferMs >= effective.bufferForPlaybackMs)
        assertTrue(effective.minBufferMs >= effective.bufferForPlaybackAfterRebufferMs)
        assertTrue(effective.maxBufferMs >= effective.minBufferMs)
        assertEquals(0, effective.backBufferDurationMs)
    }

    @Test fun `capable device retains advanced playback options`() {
        val saved = PlayerSettings(allow4kWhenFast = true, parallelNetworkEnabled = true,
            nuvioPerformanceModeEnabled = true, bufferBudgetManaged = false, allowLargeTargetBuffer = true)
        assertEquals(saved, FireTvPlaybackPolicy.apply(saved, 4_000 * mb, 1_024 * mb))
    }
    @Test fun `large app heap cannot bypass one GB physical device budget`() {
        assertEquals(48, FireTvPlaybackPolicy.managedBudgetMb(900 * mb, 1_024 * mb, 870))
        assertEquals(870, FireTvPlaybackPolicy.managedBudgetMb(4_000 * mb, 1_024 * mb, 870))
        assertEquals(25, FireTvPlaybackPolicy.managedBudgetMb(900 * mb, 128 * mb, 100))
        assertEquals(20, FireTvPlaybackPolicy.managedBudgetMb(900 * mb, 256 * mb, 20))
    }

}
