package com.nuvio.tv.core.mooncat

import com.google.gson.Gson
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import org.junit.Assert.*
import org.junit.Test

/** Public metadata captured 2026-11-11; no network requests or torrent downloads. */
class TorrentioSnapshotSelectionTest {
    private data class Entry(val name: String?, val title: String?, val filename: String?)
    private fun snapshot(name: String): List<Stream> {
        val json = checkNotNull(javaClass.getResource("/torrentio/$name.json")).readText()
        return Gson().fromJson(json, Array<Entry>::class.java).mapIndexed { index, entry ->
            Stream(name = entry.name, title = entry.title, description = null, url = null,
                ytId = null, infoHash = "fixture-$index", fileIdx = 0, externalUrl = null,
                addonName = "Torrentio", addonLogo = null,
                behaviorHints = StreamBehaviorHints(notWebReady = null, bingeGroup = null,
                    countryWhitelist = null, proxyHeaders = null, filename = entry.filename))
        }
    }

    @Test fun `real movie metadata retains Hindi and rejects oversized and ultra HD autoplay`() {
        val sources = snapshot("movie")
        assertEquals(135, sources.size)
        val ranked = PlaybackPreferences.order(sources, "en", false)
        assertTrue(ranked.isNotEmpty())
        assertTrue(MoonCatSourcePolicy.advertisesAudio(ranked.first(), "hi"))
        assertTrue(ranked.all(PlaybackPreferences::eligible))
        assertTrue(ranked.all { MoonCatSourcePolicy.qualityTier(it) == 0 })
        assertTrue(ranked.all { MoonCatSourcePolicy.sizeBytes(it)!! in PlaybackPreferences.MIN_BYTES..PlaybackPreferences.MAX_BYTES })
    }

    @Test fun `real episode metadata selects advertised Hindi before English alternatives`() {
        val sources = snapshot("episode")
        assertEquals(94, sources.size)
        val ranked = PlaybackPreferences.order(sources, "en", false)
        assertTrue(ranked.isNotEmpty())
        assertTrue(MoonCatSourcePolicy.advertisesAudio(ranked.first(), "hi"))
        assertTrue(ranked.all(PlaybackPreferences::eligible))
    }
}
