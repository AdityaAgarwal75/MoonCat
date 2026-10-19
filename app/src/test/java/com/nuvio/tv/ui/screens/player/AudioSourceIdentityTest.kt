package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceIdentityTest {
    @Test fun `old source is rejected during asynchronous resolution`() {
        assertFalse(audioSourceIdentityMatches("new", null, null, "old", null, null, "old"))
        assertTrue(audioSourceIdentityMatches("new", null, null, "new", null, null, "old"))
    }

    @Test fun `torrent candidate requires matching hash and known file index`() {
        assertFalse(audioSourceIdentityMatches(null, "NEW", 2, "local", "old", 2, "local"))
        assertFalse(audioSourceIdentityMatches(null, "NEW", 2, "local", "new", 3, "local"))
        assertTrue(audioSourceIdentityMatches(null, "NEW", 2, "local", "new", 2, "local"))
    }

    @Test fun `unresolved candidate cannot verify null URLs as equal`() {
        assertFalse(audioSourceIdentityMatches(null, null, null, null, null, null, "old"))
    }
}
