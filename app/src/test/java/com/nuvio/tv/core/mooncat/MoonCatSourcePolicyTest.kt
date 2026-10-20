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
    @Test fun `equivalent verified HTTP can outrank seed counts without overriding Hindi preference`() {
        val hindiHttp = source("1080p Hindi WEB-DL", "https://cdn.example/movie")
        val hindiTorrent = source("1080p Hindi WEB-DL 👤 999")
        val englishHttp = source("1080p English WEB-DL", "https://cdn.example/english")
        assertEquals(listOf(hindiHttp, hindiTorrent, englishHttp), MoonCatSourcePolicy.rank(
            listOf(englishHttp, hindiTorrent, hindiHttp), "hi"))
    }
    @Test fun `probe slots include free HTTP Hindi torrents and English fallbacks`() {
        val http = source("1080p Hindi", "https://cdn.example/movie")
        val torrent = source("1080p Hindi").copy(infoHash = "hindi")
        val english = source("1080p English").copy(infoHash = "english")
        val streams = listOf(torrent, english, http)
        assertEquals(listOf(http), MoonCatSourcePolicy.probePool(streams, 0, "hi"))
        assertEquals(listOf(torrent), MoonCatSourcePolicy.probePool(streams, 2, "hi"))
        assertEquals(listOf(english), MoonCatSourcePolicy.probePool(streams, 6, "hi"))
    }
    @Test fun `unlabelled source cannot bypass the structured resolution cap`() {
        assertFalse(MoonCatSourcePolicy.suitable(source("Video").copy(qualityValue = 2160)))
        listOf("UHD Hindi", "Ultra HD English", "3840x2160 Hindi", "4-K", "1440p")
            .forEach { assertFalse(it, MoonCatSourcePolicy.suitable(source(it))) }
    }

    @Test fun `playback candidates keep English fallback after Hindi and exclude other audio`() {
        val hi = source("720p Hindi")
        val en = source("1080p English")
        val unknown = source("1080p")
        assertEquals(listOf(hi, en, unknown), MoonCatSourcePolicy.playbackCandidates(
            listOf(en, unknown, source("1080p French"), hi)))
    }

    @Test fun `clean digital release outranks an unknown release at equal resolution`() {
        val clean = source("1080p WEB-DL Hindi 👤 100")
        val unknown = source("1080p Hindi 👤 900")
        assertEquals(clean, MoonCatSourcePolicy.rank(listOf(unknown, clean)).first())
    }
    @Test fun `provider branding does not misclassify an explicitly1080 release as4k`() {
        assertTrue(MoonCatSourcePolicy.suitable(source("4KHDHub 1080p Hindi WEB-DL 3 GB")))
        assertTrue(MoonCatSourcePolicy.suitable(source("1080p UHD BluRay Hindi 3 GB")))
        assertFalse(MoonCatSourcePolicy.suitable(source("4KHDHub 2160p Hindi 3 GB")))
    }

    @Test fun `4k remux and large releases are excluded`() {
        listOf("Film 2160p 3 GB", "Film 4K 2 GB", "Film 1080p REMUX", "Film 1080p 40 GB", "Film 1080p 7 GiB", "Film 1080p CAM", "Film 1080p TeleSync", "Film 480p 1 GB")
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
        assertEquals(listOf(english), MoonCatSourcePolicy.forPreferredLanguage(listOf(english, unknown), "hi"))
        assertEquals(listOf(english), MoonCatSourcePolicy.forPreferredLanguage(listOf(english), "hi"))
    }
    @Test fun `decimal and binary size units differ`() {
        assertEquals(7_000_000_000L, MoonCatSourcePolicy.sizeBytes(source("7 GB")))
        assertEquals(7_516_192_768L, MoonCatSourcePolicy.sizeBytes(source("7 GiB")))
        assertEquals(700_000_000L, MoonCatSourcePolicy.sizeBytes(source("700 MB")))
    }
    @Test fun `subtitle labels cannot establish Hindi dubbed audio`() {
        assertFalse(MoonCatSourcePolicy.advertisesAudio(source("English 1080p | Subtitles: Hindi"), "hi"))
        assertFalse(MoonCatSourcePolicy.advertisesAudio(source("Japanese 1080p Hindi Subs"), "hi"))
        assertTrue(MoonCatSourcePolicy.advertisesAudio(source("Film.1080p.HIN-ENG.WEB-DL"), "hi"))
        assertTrue(MoonCatSourcePolicy.advertisesAudio(source("Film [HI + EN] BluRay"), "en"))
    }
    @Test fun `large camera releases remain excluded regardless of claimed HD resolution`() {
        listOf("HD-CAM 1080p 5 GB", "HDTS Hindi 4 GB", "HDTC 5 GB", "HQCAM 1080p", "HQ-TS Hindi", "TSRip", "PDVDRip", "TS 1080p", "TC Hindi", "PreDVD 1080p").forEach {
            assertFalse(it, MoonCatSourcePolicy.suitable(source(it)))
        }
        assertTrue(MoonCatSourcePolicy.suitable(source("BluRay 1080p Hindi English 5 GB")))
        assertTrue(MoonCatSourcePolicy.suitable(source("WEBRip 1080p 2 GB")))
    }
    @Test fun `English dub beats Japanese original when Hindi is absent`() {
        val en = source("English 720p"); val ja = source("Japanese 1080p")
        assertEquals(listOf(en), MoonCatSourcePolicy.forPreferredLanguage(listOf(ja,en), "hi"))
    }
    @Test fun `torrent availability breaks ties within a quality tier`() {
        val low = source("1080p Hindi 👤 2"); val high = source("1080p Hindi 👤 240")
        assertEquals(high, MoonCatSourcePolicy.rank(listOf(low, high)).first())
    }
    @Test fun `old cached camera and wrong-language releases cannot bypass the new policy`() {
        assertFalse(MoonCatSourcePolicy.reusableRelease("1080p", "Movie.HDCAM.Hindi.mkv", 5_000_000_000L, "hi", 7_000_000_000L))
        assertFalse(MoonCatSourcePolicy.reusableRelease("1080p", "Movie.Japanese.WEB-DL.mkv", null, "hi", 7_000_000_000L))
        assertTrue(MoonCatSourcePolicy.reusableRelease("1080p", "Movie.HIN.ENG.WEB-DL.mkv", 2_000_000_000L, "hi", 7_000_000_000L))
    }
}
