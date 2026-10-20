package com.nuvio.tv.core.mooncat

import com.nuvio.tv.ui.screens.collection.mooncatStudios
import com.nuvio.tv.ui.screens.news.mooncatNewsChannels
import org.junit.Assert.assertTrue
import org.junit.Test

class MooncatDiscoverySmokeTest {
    @Test fun `studio catalogue has films and series in major collections`() {
        val names = mooncatStudios.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("Marvel", "DC", "Wizarding World", "HBO", "Paramount")))
        assertTrue(mooncatStudios.size >= 10)
        assertTrue(mooncatStudios.sumOf { it.works.size } >= 40)
        assertTrue(mooncatStudios.flatMap { it.works }.all { it.imdb.matches(Regex("tt[0-9]{7,9}")) })
        assertTrue(mooncatStudios.flatMap { it.works }.any { it.type == "series" })
    }

    @Test fun `news channels only use internal HLS stream addresses`() {
        assertTrue(mooncatNewsChannels.size >= 3)
        assertTrue(mooncatNewsChannels.all { it.url.startsWith("https://") && it.url.contains(".m3u8") })
    }
}
