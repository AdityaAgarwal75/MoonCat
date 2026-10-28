package com.nuvio.tv.data.repository

import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import org.junit.Assert.*
import org.junit.Test

class StreamDiscoveryIdentityTest {
    private fun stream(url: String? = "https://source.test/movie", hash: String? = null, fileIdx: Int? = null,
                       referer: String? = null, description: String? = null) = Stream(
        name = "1080p", title = "Movie", description = description, url = url,
        ytId = null, infoHash = hash, fileIdx = fileIdx, externalUrl = null,
        behaviorHints = StreamBehaviorHints(null, null, null,
            referer?.let { ProxyHeaders(mapOf("Referer" to it), null) }),
        addonName = "Provider", addonLogo = null
    )

    @Test fun `same torrent does not collapse ready HTTP and local torrent transports`() {
        assertNotEquals(stream(hash = "abc").discoveryIdentity(), stream(url = null, hash = "ABC").discoveryIdentity())
    }
    @Test fun `different episode file indexes remain distinct`() {
        assertNotEquals(stream(hash = "abc", fileIdx = 1).discoveryIdentity(), stream(hash = "abc", fileIdx = 2).discoveryIdentity())
    }
    @Test fun `header dependent requests remain distinct`() {
        assertNotEquals(stream(referer = "https://one.test").discoveryIdentity(), stream(referer = "https://two.test").discoveryIdentity())
    }
    @Test fun `header names are case insensitive`() {
        val source = stream(referer = "https://one.test")
        val alternate = source.copy(behaviorHints = source.behaviorHints!!.copy(
            proxyHeaders = ProxyHeaders(mapOf("referer" to "https://one.test"), null)))
        assertEquals(source.discoveryIdentity(), alternate.discoveryIdentity())
    }
    @Test fun `dub evidence from alternate labels is retained`() {
        assertNotEquals(stream(description = "English").discoveryIdentity(), stream(description = "Hindi English").discoveryIdentity())
    }
    @Test fun `availability annotation does not change request identity`() {
        val source = stream()
        assertEquals(source.discoveryIdentity(), source.copy(badges = emptyList()).discoveryIdentity())
    }
}
