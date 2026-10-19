package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.*
import org.junit.Test

internal fun source(name: String, url: String? = null) = Stream(
    name = name, title = null, description = null, url = url, ytId = null,
    infoHash = if (url == null) "abc" else null, fileIdx = null, externalUrl = null,
    behaviorHints = null, addonName = "Torrentio", addonLogo = null
)

class MoonCatSourcePolicyTest {
    @Test fun `4k remux and large releases are excluded`() {
        listOf("Film 2160p 3 GB", "Film 4K 2 GB", "Film 1080p REMUX", "Film 1080p 40 GB", "Film 1080p 7 GiB")
            .forEach { assertFalse(it, MoonCatSourcePolicy.suitable(source(it))) }
        assertTrue(MoonCatSourcePolicy.suitable(source("Film Hindi English Japanese 1080p 2.5 GB")))
    }
    @Test fun `ranking prefers1080 then720 without removing multi audio releases`() {
        val ranked = MoonCatSourcePolicy.rank(listOf(source("Unknown"), source("720p"), source("1080p multi audio")))
        assertEquals(listOf("1080p multi audio", "720p", "Unknown"), ranked.map { it.name })
    }
    @Test fun `episode size limit applies even without parsed client metadata`() {
        assertFalse(MoonCatSourcePolicy.suitable(source("1080p 3 GB"), 2_000_000_000L))
        assertTrue(MoonCatSourcePolicy.suitable(source("1080p 1.5 GB"), 2_000_000_000L))
    }
    @Test fun `Hindi preference prioritizes Hindi over English1080 and rejects known wrong audio`() {
        val hindi = source("Hindi 720p")
        val english = source("English 1080p")
        val unknown = source("1080p unlabelled")
        assertEquals(listOf(hindi), MoonCatSourcePolicy.forPreferredLanguage(listOf(english, unknown, hindi), "hi"))
        assertEquals(listOf(unknown), MoonCatSourcePolicy.forPreferredLanguage(listOf(english, unknown), "hi"))
        assertTrue(MoonCatSourcePolicy.forPreferredLanguage(listOf(english), "hi").isEmpty())
    }
    @Test fun `decimal and binary size units differ`() {
        assertEquals(7_000_000_000L, MoonCatSourcePolicy.sizeBytes(source("7 GB")))
        assertEquals(7_516_192_768L, MoonCatSourcePolicy.sizeBytes(source("7 GiB")))
        assertEquals(700_000_000L, MoonCatSourcePolicy.sizeBytes(source("700 MB")))
    }
}
