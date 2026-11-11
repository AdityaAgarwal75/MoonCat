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
        val ranked = MoonCatSourcePolicy.instantOrder(sources, "hi")
        assertEquals(51, ranked.size)
        assertEquals(6, ranked.count { MoonCatSourcePolicy.advertisesAudio(it, "hi") })
        // The old 3.31 GB RONIN preference has only 2 seeds. The 3.7 GB
        // REMASTERED release has 3, so availability must now win.
        assertTrue(ranked.first().title.orEmpty().contains("REMASTERED"))
        assertEquals(3, MoonCatSourcePolicy.advertisedSeeders(ranked.first()))
        assertTrue(ranked.take(6).all { MoonCatSourcePolicy.advertisesAudio(it, "hi") })
        assertTrue(ranked.none { MoonCatSourcePolicy.is4k(it) })
        assertTrue(ranked.all { (MoonCatSourcePolicy.sizeBytes(it) ?: 0) <= 4_000_000_000L })
    }

    @Test fun `real episode metadata selects advertised Hindi before English alternatives`() {
        val sources = snapshot("episode")
        assertEquals(94, sources.size)
        val ranked = MoonCatSourcePolicy.instantOrder(sources, "hi")
        assertEquals(65, ranked.size)
        assertEquals(7, ranked.count { MoonCatSourcePolicy.advertisesAudio(it, "hi") })
        assertTrue(ranked.first().behaviorHints?.filename.orEmpty().contains("The.PunisheR"))
        assertTrue(ranked.take(7).all { MoonCatSourcePolicy.advertisesAudio(it, "hi") })
        assertTrue(MoonCatSourcePolicy.instantOrder(sources, "en").take(1).all {
            MoonCatSourcePolicy.advertisesAudio(it, "en")
        })
    }
}
