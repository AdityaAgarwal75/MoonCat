package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.ProxyHeaders
import org.junit.Assert.*
import org.junit.Test

class SourceStreamMergeTest {
    private fun stream(url: String, auth: String = "first") = Stream(
        name = "Hindi 1080p", title = null, description = null, url = url, ytId = null,
        infoHash = "same-content", fileIdx = 1, externalUrl = null,
        behaviorHints = StreamBehaviorHints(null, null, null, ProxyHeaders(mapOf("Authorization" to auth), null)),
        addonName = "Provider", addonLogo = null
    )

    @Test fun `resume retains distinct HTTP routes even with the same content hash`() {
        val first = stream("https://a/file")
        val mirror = stream("https://b/file")
        assertEquals(listOf(first, mirror), mergeSourceStreams(listOf(first), listOf(mirror)))
    }

    @Test fun `resume retains distinct credential variants for one URL`() {
        val first = stream("https://a/file")
        val second = stream("https://a/file", "second")
        assertEquals(2, mergeSourceStreams(listOf(first), listOf(second)).size)
    }

    @Test fun `refresh replaces the same request with current metadata`() {
        val first = stream("https://a/file")
        val fresh = first.copy(description = "New metadata")
        assertEquals(listOf(fresh), mergeSourceStreams(listOf(first), listOf(fresh)))
    }
}
