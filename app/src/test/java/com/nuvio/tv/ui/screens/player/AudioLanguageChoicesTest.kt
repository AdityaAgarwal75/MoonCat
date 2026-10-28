package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLanguageChoicesTest {
    private fun track(index: Int, language: String?, channels: Int = 2, name: String = "Audio", supported: Boolean = true) =
        TrackInfo(index, name, language, channelCount = channels, isSupported = supported)

    @Test fun `aliases and regions collapse to one real language`() {
        val choices = AudioLanguageChoices.choices(listOf(track(0, "hin"), track(1, "hi-IN"), track(2, "eng"), track(3, "jpn")))
        assertEquals(listOf("Hindi", "English", "Japanese"), choices.map { it.name })
    }

    @Test fun `supported normal stereo beats commentary and unsupported track`() {
        val choices = AudioLanguageChoices.choices(listOf(track(0, "en", name = "Commentary"), track(1, "en", 6), track(2, "en"), track(3, "en", supported = false)))
        assertEquals(2, choices.single().index)
    }

    @Test fun `language selection indicator follows language not codec track`() {
        val tracks = listOf(track(0, "en", 6), track(1, "eng"))
        val choices = AudioLanguageChoices.choices(tracks)
        assertEquals(choices.single().index, AudioLanguageChoices.selectedChoiceIndex(tracks, 0, choices))
    }

    @Test fun `missing preferred languages are clearly source searches`() {
        val choices = AudioLanguageChoices.menuChoices(listOf(track(0, "jpn")))
        assertEquals("Japanese", choices.first().name)
        assertEquals(listOf("hi", "en"), choices.drop(1).map { AudioLanguageChoices.searchLanguage(it.index) })
        assertTrue(choices.drop(1).all { it.name.contains("Search sources") })
    }

    @Test fun `container audio labels recover a missing language tag`() {
        val choices = AudioLanguageChoices.choices(listOf(track(4, null, name = "Hindi AAC 2.0"), track(9, "und", name = "English EAC3")))
        assertEquals(listOf("hi", "en"), choices.map { it.language })
        assertEquals(listOf(4, 9), choices.map { it.index })
    }

    @Test fun `ambiguous multi audio track label is not proof of Hindi`() {
        val choices = AudioLanguageChoices.choices(listOf(track(0, null, name = "Hindi English Dual Audio")))
        assertEquals("und", choices.single().language)
    }

    @Test fun `unknown stays unknown and absent languages are never invented`() {
        val choices = AudioLanguageChoices.choices(listOf(track(0, null), track(1, "und")))
        assertEquals(2, choices.size)
        assertTrue(choices.all { it.name.contains("unknown") })
        assertEquals(0, AudioLanguageChoices.selectedChoiceIndex(listOf(track(0, null), track(1, "und")), 0, choices))
        assertEquals(1, AudioLanguageChoices.selectedChoiceIndex(listOf(track(0, null), track(1, "und")), 1, choices))
        assertTrue(AudioLanguageChoices.choices(emptyList()).isEmpty())
    }
}
