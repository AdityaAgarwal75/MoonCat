package com.nuvio.tv.core.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneHandoffProtocolTest {
    private val key = "a".repeat(64)
    private val nonce = "b".repeat(32)
    private val now = 1_750_600_000L
    private val movie = """{"contentId":"tt0371746","videoId":"tt0371746","type":"movie","title":"Iron Man","positionMs":84712}"""

    @Test fun signatureRequiresCorrectBodyKeyAndFreshTime() {
        val auth = PhoneHandoffAuthenticator(key) { now }
        val signature = signPhoneHandoff(key, "/play", now.toString(), nonce, movie)
        assertFalse(auth.accept("/play",now.toString(), nonce, signature, "changed"))
        assertFalse(auth.accept("/play",now.toString(), nonce, signPhoneHandoff("c".repeat(64), "/play", now.toString(), nonce, movie), movie))
        assertFalse(auth.accept("/play",(now - 46).toString(), nonce, signPhoneHandoff(key, "/play", (now - 46).toString(), nonce, movie), movie))
        assertTrue(auth.accept("/play",now.toString(), nonce, signature, movie))
        assertFalse(auth.accept("/play",now.toString(), nonce, signature, movie))
    }

    @Test fun acceptsOnlyCanonicalMovieAndEpisodeRoutes() {
        assertEquals(84712L, parsePhoneHandoff(movie)?.positionMs)
        assertNull(parsePhoneHandoff(movie.replace("84712", "-1")))
        assertNull(parsePhoneHandoff(movie.replace("84712", "4.8")))
        assertNull(parsePhoneHandoff(movie.replace("tt0371746", "https://example.com/a")))
        val episode = """{"contentId":"tt0944947","videoId":"tt0944947:8:6","type":"series","title":"Game of Thrones","season":8,"episode":6,"positionMs":0}"""
        assertNotNull(parsePhoneHandoff(episode))
        assertNull(parsePhoneHandoff(episode.replace(":8:6", ":7:6")))
    }

    @Test fun acceptsOnlyPrivateIpv4() {
        assertTrue(isPrivatePhoneHandoffAddress("192.168.0.20"))
        assertTrue(isPrivatePhoneHandoffAddress("172.31.4.5"))
        assertFalse(isPrivatePhoneHandoffAddress("172.32.4.5"))
        assertFalse(isPrivatePhoneHandoffAddress("127.0.0.1"))
        assertFalse(isPrivatePhoneHandoffAddress("192.168.300.1"))
    }
}
