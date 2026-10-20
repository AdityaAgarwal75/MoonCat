package com.nuvio.tv.core.mooncat

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MoonCatSourceRaceTest {
    @Test fun `responsive source wins against failed endpoint with range request`() = runBlocking {
        MockWebServer().use { dead ->
            MockWebServer().use { live ->
                dead.enqueue(MockResponse().setResponseCode(503))
                live.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-4/100").setBody("media"))
                val bad = source("1080p bad", dead.url("/bad").toString())
                val good = source("1080p good", live.url("/good").toString())
                assertEquals(good, MoonCatSourceRace.choose(listOf(bad, good)))
                assertEquals("bytes=0-65535", live.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range"))
            }
        }
    }
    @Test fun `equal size media samples prefer the faster responding server`() = runBlocking {
        MockWebServer().use { slow ->
            MockWebServer().use { fast ->
                slow.enqueue(MockResponse().setBody("x".repeat(65536)).setBodyDelay(300, TimeUnit.MILLISECONDS))
                fast.enqueue(MockResponse().setBody("x".repeat(65536)))
                val slowSource = source("1080p slow", slow.url("/video").toString())
                val fastSource = source("1080p fast", fast.url("/video").toString())
                assertEquals(fastSource, MoonCatSourceRace.choose(listOf(slowSource, fastSource)))
            }
        }
    }
    @Test fun `all failed http endpoints produce no automatic selection`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("error page"))
            assertNull(MoonCatSourceRace.choose(listOf(source("1080p", server.url("/").toString()))))
        }
    }
    @Test fun `partial range must match requested start length and total`() {
        assertEquals(65536L, MoonCatSourceRace.validContentRange("bytes 0-65535/1000000", 65536))
        assertNull(MoonCatSourceRace.validContentRange(null, 65536))
        assertNull(MoonCatSourceRace.validContentRange("bytes 0-65535/10", 65536))
        assertNull(MoonCatSourceRace.validContentRange("bytes 1-65535/1000000", 65535))
        assertNull(MoonCatSourceRace.validContentRange("bytes 0-99999/1000000", 100000))
        assertNull(MoonCatSourceRace.validContentRange("bytes 0-65535/1000000", 64))
    }
    @Test fun `addon credentials are omitted on plaintext probes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("video"))
            val protected = source("1080p", server.url("/video").toString()).copy(
                behaviorHints = com.nuvio.tv.domain.model.StreamBehaviorHints(
                    notWebReady = null, bingeGroup = null, countryWhitelist = null,
                    proxyHeaders = com.nuvio.tv.domain.model.ProxyHeaders(
                        request = mapOf("Authorization" to "secret", "Cookie" to "session=secret", "X-Api-Key" to "secret"),
                        response = null
                    )
                )
            )
            assertNotNull(MoonCatSourceRace.choose(listOf(protected)))
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertNull(request?.getHeader("Authorization"))
            assertNull(request?.getHeader("Cookie"))
            assertNull(request?.getHeader("X-Api-Key"))
        }
    }
    @Test fun `torrent fallback does not issue a network probe`() = runBlocking {
        val torrent = source("1080p Hindi English Japanese 2 GB")
        assertEquals(torrent, MoonCatSourceRace.choose(listOf(torrent)))
    }
    @Test fun `race probes no more than seven sources`() = runBlocking {
        MockWebServer().use { server ->
            repeat(8) { server.enqueue(MockResponse().setBody("video")) }
            val candidates = (1..8).map { source("1080p $it", server.url("/$it").toString()) }
            assertNotNull(MoonCatSourceRace.choose(candidates))
            assertEquals(7, server.requestCount)
        }
    }
}
