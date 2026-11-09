package com.nuvio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRecoveryPositionTest {
    @Test fun `manual switch before metadata resolves still loads saved resume`() {
        org.junit.Assert.assertNull(manualSourceSwitchPosition(0, null, null, false, false))
    }

    @Test fun `manual switch retains explicit restart and pending seek`() {
        assertEquals(0L, manualSourceSwitchPosition(0, null, null, false, true))
        assertEquals(50_000L, manualSourceSwitchPosition(0, 50_000, null, false, false))
    }

    @Test fun `manual switch during playback retains actual playhead`() {
        assertEquals(75_000L, manualSourceSwitchPosition(75_000, null, null, true, false))
    }
    @Test fun `failure before first frame retains pending saved resume`() {
        assertEquals(1_800_000L, sourceRecoveryPosition(0, null, 1_800_000))
    }

    @Test fun `explicit pending seek takes precedence over saved resume`() {
        assertEquals(2_400_000L, sourceRecoveryPosition(1_000, 2_400_000, 1_800_000))
    }

    @Test fun `active playback uses current position and restart stays at zero`() {
        assertEquals(2_000_000L, sourceRecoveryPosition(2_000_000, null, null))
        assertEquals(0L, sourceRecoveryPosition(0, null, null))
        assertEquals(0L, sourceRecoveryPosition(-1, null, null))
    }
}
