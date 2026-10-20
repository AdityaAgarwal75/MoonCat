package com.nuvio.tv.core.mooncat

import com.nuvio.tv.ui.screens.collection.mooncatStudios
import com.nuvio.tv.ui.screens.news.mooncatNewsChannels
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.ui.screens.collection.MooncatTitle
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MooncatDiscoverySmokeTest {
    @Test fun `studio catalogues contain real selectable film and series ids`() {
        assertTrue(mooncatStudios.map { it.name }.containsAll(listOf("Marvel", "DC", "Wizarding World", "HBO", "Paramount")))
        val works = mooncatStudios.flatMap { studio ->
            val file = File("src/main/assets/studios/${studio.id}.json.gz")
            val entries: List<MooncatTitle> = Gson().fromJson(java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }, object : TypeToken<List<MooncatTitle>>() {}.type)
            assertTrue(studio.name, entries.isNotEmpty())
            assertEquals(entries.size, entries.distinctBy { it.imdb }.size)
            assertTrue(entries.all { it.imdb.matches(Regex("tt[0-9]{7,9}")) && it.title.isNotBlank() })
            if (studio.id == "wizarding") assertEquals(11, entries.size)
            entries
        }
        assertTrue(works.size > 10000)
        assertTrue(works.any { it.type == "series" })
    }
    @Test fun `twenty unique channels have logos and internal HLS addresses`() {
        assertEquals(20, mooncatNewsChannels.size)
        assertEquals(20, mooncatNewsChannels.distinctBy { it.name }.size)
        assertTrue(mooncatNewsChannels.all { it.logo != 0 && it.url.startsWith("https://") && it.url.contains(".m3u8") })
    }
}
