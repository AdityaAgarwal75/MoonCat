package com.nuvio.tv.domain.playback

import org.junit.Assert.*
import org.junit.Test

class AudioLanguagePolicyTest {
    private fun track(key: Int, language: String?, label: String = "Audio", supported: Boolean = true,
                      channels: Int? = 2, selected: Boolean = false) =
        AudioLanguagePolicy.Track(key, language, label, supported, channels, selected = selected)

    @Test fun `normalizes ISO aliases regions and case across all supplied languages`() {
        assertEquals(listOf("hi", "en", "ja", "ta", "te", "fr", "de", "zh"),
            listOf(" HIN ", "en_US", "JPN", "tam", "tel", "fre", "deu", "zho")
                .map(AudioLanguagePolicy::normalize))
    }

    @Test fun `selects actual supported Hindi track before changing file`() {
        val tracks = listOf(track(3, "eng", selected = true), track(19, "hi-IN"), track(2, "hin", supported = false))
        assertEquals(19, AudioLanguagePolicy.matching(tracks, "hin").single().key)
        assertFalse(AudioLanguagePolicy.confirmed(tracks, "hi"))
        assertTrue(AudioLanguagePolicy.confirmed(tracks.map { it.copy(selected = it.key == 19) }, "hi"))
    }

    @Test fun `clear track label can fill absent tag but conflicting or dual labels cannot`() {
        assertEquals("hi", AudioLanguagePolicy.language("und", "Hindi - AAC 2.0"))
        assertEquals("en", AudioLanguagePolicy.language("eng", "Hindi"))
        assertNull(AudioLanguagePolicy.language(null, "Hindi English Dual Audio"))
        assertNull(AudioLanguagePolicy.language("mul", "Multi audio AAC"))
    }

    @Test fun `normal stereo is preferred to commentary and unsupported tracks`() {
        val tracks = listOf(track(0, "hi", "Hindi commentary"), track(1, "hi", channels = 8),
            track(2, "hin", channels = 6), track(9, "hi", channels = 2), track(3, "hi", supported = false))
        assertEquals(listOf(9, 2, 1, 0), AudioLanguagePolicy.matching(tracks, "hi").map { it.key })
    }

    @Test fun `source hints support several dubs without reading subtitle declarations as audio`() {
        assertEquals(setOf("hi", "en", "ja", "ta", "te", "ko", "fr", "de"),
            AudioLanguagePolicy.advertisedLanguages("Hindi ENG Japanese Tamil Telugu Korean French German"))
        assertEquals(setOf("ja"), AudioLanguagePolicy.advertisedLanguages("Japanese audio | Hindi subtitles"))
        assertEquals(setOf("hi"), AudioLanguagePolicy.advertisedLanguages("Hindi AAC | Subtitles: English"))
        assertTrue(AudioLanguagePolicy.advertisedLanguages("Dual Audio Multi 1080p").isEmpty())
    }

    @Test fun `no successful confirmation for a requested but unselected language`() {
        val tracks = listOf(track(0, "ja", selected = true), track(1, "hi", selected = false))
        assertFalse(AudioLanguagePolicy.confirmed(tracks, "hi"))
        assertTrue(AudioLanguagePolicy.confirmed(tracks, "jpn"))
    }
}
