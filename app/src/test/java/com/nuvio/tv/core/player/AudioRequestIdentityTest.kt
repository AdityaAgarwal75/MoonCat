package com.nuvio.tv.core.player

import org.junit.Assert.*
import org.junit.Test

class AudioRequestIdentityTest {
    @Test fun `same torrent hash does not confirm a different HTTP route`() {
        assertFalse(audioPlaybackRequestMatches("https://b/file", "HASH", 1, emptyMap(),
            "https://a/file", "HASH", 1, emptyMap()))
        assertTrue(audioPlaybackRequestMatches("https://b/file", "HASH", 1, emptyMap(),
            "https://b/file", "HASH", 1, emptyMap()))
    }

    @Test fun `same URL with different credentials is not the requested route`() {
        assertFalse(audioPlaybackRequestMatches("https://media/file", null, null, mapOf("Authorization" to "second"),
            "https://media/file", null, null, mapOf("Authorization" to "first")))
        assertTrue(audioPlaybackRequestMatches("https://media/file", null, null, mapOf("Authorization" to "second"),
            "https://media/file", null, null, mapOf("authorization" to "second")))
    }

    @Test fun `true torrent confirmation requires hash and advertised file index`() {
        assertTrue(audioPlaybackRequestMatches(null, "HASH", 2, emptyMap(), "http://localhost/file", "hash", 2, emptyMap()))
        assertFalse(audioPlaybackRequestMatches(null, "HASH", 2, emptyMap(), "http://localhost/file", "hash", 3, emptyMap()))
        assertFalse(audioPlaybackRequestMatches(null, null, null, emptyMap(), null, null, null, emptyMap()))
    }
}
