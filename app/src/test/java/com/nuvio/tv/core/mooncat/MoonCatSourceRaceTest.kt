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
    @Test fun `torrent requires native verification instead of an HTTP fallback`() = runBlocking {
        val torrent = source("1080p Hindi English Japanese 2 GB")
        assertNull(MoonCatSourceRace.choose(listOf(torrent)))
    }
    @Test fun `race probes no more than eight sources`() = runBlocking {
        MockWebServer().use { server ->
            repeat(8) { server.enqueue(MockResponse().setBody("video")) }
            val candidates = (1..12).map { source("1080p $it", server.url("/$it").toString()) }
            assertNotNull(MoonCatSourceRace.choose(candidates))
            assertEquals(8, server.requestCount)
        }
    }

    @Test fun `headroom preserves1080 quality instead of always picking fastest720`() {
        val hd = source("1080p Hindi WEB-DL")
        val small = source("720p Hindi WEB-DL")
        assertEquals(hd, MoonCatSourceRace.selectMeasured(listOf(hd to 1_500_000.0, small to 6_000_000.0)))
        assertEquals(small, MoonCatSourceRace.selectMeasured(listOf(hd to 80_000.0, small to 1_500_000.0)))
    }

    @Test fun `Hindi wins among viable files while English remains a fallback`() {
        val hindi = source("720p Hindi")
        val english = source("1080p English")
        assertEquals(hindi, MoonCatSourceRace.selectMeasured(listOf(hindi to 1_000_000.0, english to 4_000_000.0)))
        assertEquals(english, MoonCatSourceRace.selectMeasured(listOf(hindi to 0.0, english to 4_000_000.0)))
    }

    @Test fun `HLS chooses a1080 variant and rejects an exclusively4k manifest`() {
        val master = """#EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=20000000,RESOLUTION=3840x2160
            ultra.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            hd.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
            small.m3u8
        """.trimIndent()
        assertEquals("https://cdn.example/hd.m3u8", MoonCatSourceRace.playlistTarget(master, "https://cdn.example/master.m3u8"))
        assertNull(MoonCatSourceRace.playlistTarget("#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=3840x2160\nultra.m3u8", "https://cdn.example/master.m3u8"))
        assertEquals("https://cdn.example/video/segment.ts", MoonCatSourceRace.playlistTarget(
            "#EXTM3U\n#EXTINF:6.0,\nsegment.ts", "https://cdn.example/video/hd.m3u8"))
    }

    @Test fun `HLS readiness reads media rather than measuring the playlist`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6.0,\nsegment.ts"))
            server.enqueue(MockResponse().setBody("x".repeat(65536)))
            val stream = source("1080p Hindi", server.url("/list.m3u8").toString())
            assertEquals(stream, MoonCatSourceRace.choose(listOf(stream)))
            assertEquals("/list.m3u8", server.takeRequest().path)
            assertEquals("/segment.ts", server.takeRequest().path)
        }
    }
}
