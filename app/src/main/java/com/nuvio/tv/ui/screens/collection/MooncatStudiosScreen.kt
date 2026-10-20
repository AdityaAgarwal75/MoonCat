@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.components.ContentCard
import com.nuvio.tv.ui.components.PosterCardStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MooncatTitle(val title: String, val imdb: String, val type: String = "movie", val year: String = "") {
    fun preview() = MetaPreview(
        id = imdb, type = if (type == "series") ContentType.SERIES else ContentType.MOVIE,
        name = title, poster = "https://images.metahub.space/poster/medium/$imdb/img",
        posterShape = PosterShape.POSTER, background = null, logo = null, description = null,
        releaseInfo = year.takeIf { it.isNotBlank() }, imdbRating = null, genres = emptyList()
    )
}
data class MooncatStudio(val id: String, val name: String, val logo: Int)
val mooncatStudios = listOf(
    MooncatStudio("marvel", "Marvel", R.drawable.studio_marvel),
    MooncatStudio("dc", "DC", R.drawable.studio_dc),
    MooncatStudio("wizarding", "Wizarding World", R.drawable.studio_wizarding),
    MooncatStudio("hbo", "HBO", R.drawable.studio_hbo),
    MooncatStudio("paramount", "Paramount", R.drawable.studio_paramount),
    MooncatStudio("starwars", "Star Wars", R.drawable.studio_starwars),
    MooncatStudio("pixar", "Pixar", R.drawable.studio_pixar),
    MooncatStudio("dreamworks", "DreamWorks", R.drawable.studio_dreamworks),
    MooncatStudio("disney", "Disney", R.drawable.studio_disney),
    MooncatStudio("universal", "Universal", R.drawable.studio_universal),
    MooncatStudio("sony", "Sony / Columbia", R.drawable.studio_sony)
)

@Composable
fun MooncatStudiosScreen(onOpen: (MooncatTitle) -> Unit) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable(selectedId) { mutableStateOf("all") }
    var visibleCount by rememberSaveable(selectedId, filter) { mutableIntStateOf(60) }
    var catalogues by remember { mutableStateOf<Map<String, List<MooncatTitle>>>(emptyMap()) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        error = false
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val gson = Gson()
                val type = object : TypeToken<List<MooncatTitle>>() {}.type
                mooncatStudios.associate { studio ->
                    studio.id to java.util.zip.GZIPInputStream(context.assets.open("studios/${studio.id}.json.gz")).bufferedReader().use {
                        gson.fromJson<List<MooncatTitle>>(it, type).distinctBy { work -> work.imdb }
                    }
                }
            }
        }
        loaded.onSuccess { catalogues = it }.onFailure { error = true }
    }
    BackHandler(selectedId != null) { selectedId = null }
    val selected = mooncatStudios.firstOrNull { it.id == selectedId }
    val catalogue = catalogues[selectedId].orEmpty().filter { filter == "all" || it.type == filter }
    Column(Modifier.fillMaxSize().background(Color(0xFF080B14))) {
        Column(Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 8.dp)) {
            Text(selected?.name ?: "Studios & Collections", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (selected == null) "Find your next film. Explore the worlds you love."
                else "${catalogue.size} titles  ·  ${if (filter == "all") "Movies & series" else if (filter == "movie") "Movies" else "Series"}",
                color = Color(0xFFA8B0C4), modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (error) {
            Text("Unable to load collections", Modifier.padding(32.dp))
            Button(onClick = { retry++ }, modifier = Modifier.padding(horizontal = 32.dp)) { Text("Retry") }
        } else if (catalogues.isEmpty()) {
            Text("Loading collections…", Modifier.padding(32.dp))
        } else if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(mooncatStudios, key = { it.id }) { studio ->
                            Card(onClick = { selectedId = studio.id }, modifier = Modifier.size(160.dp, 90.dp)) {
                                Image(painterResource(studio.logo), studio.name,
                                    Modifier.fillMaxSize().background(Color(0xFFF1F1F4)).padding(16.dp), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
                items(mooncatStudios, key = { it.id }) { studio ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(studio.name, style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { selectedId = studio.id }) { Text("Browse ${catalogues[studio.id].orEmpty().size} titles  ›") }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(catalogues[studio.id].orEmpty().take(16), key = { it.imdb }) { work -> StudioPoster(work, onOpen) }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { selectedId = null }) { Text("‹ All studios") }
                listOf("all" to "All titles", "movie" to "Movies", "series" to "Series").forEach { (value, label) ->
                    Button(onClick = { filter = value }) { Text(if (filter == value) "● $label" else label) }
                }
            }
            // Rows retain the same focusable poster component as Home. Load incrementally for TV memory.
            key(selectedId, filter) {
                LazyColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(catalogue.take(visibleCount).chunked(6)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { row.forEach { work -> StudioPoster(work, onOpen) } }
                    }
                    if (catalogue.isEmpty()) item { Text("No titles in this category. Try All titles.", color = Color(0xFFA8B0C4)) }
                    if (visibleCount < catalogue.size) item {
                        Button(onClick = { visibleCount += 60 }) { Text("Load more  ·  ${visibleCount.coerceAtMost(catalogue.size)} / ${catalogue.size}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioPoster(work: MooncatTitle, onOpen: (MooncatTitle) -> Unit) {
    ContentCard(item = work.preview(), posterCardStyle = PosterCardStyle(width = 118.dp, height = 177.dp),
        showImdbRatings = false, onClick = { onOpen(work) })
}
