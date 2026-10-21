@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nuvio.tv.ui.theme.NuvioTheme
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
    var catalogues by remember { mutableStateOf<Map<String, List<MooncatTitle>>>(emptyMap()) }
    var failed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(retry) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val gson = Gson()
            val type = object : TypeToken<List<MooncatTitle>>() {}.type
            mooncatStudios.associate { studio ->
                studio.id to runCatching {
                    // AAPT expands .json.gz assets while packaging the APK.
                    val input = runCatching { context.assets.open("studios/${studio.id}.json") }
                        .getOrElse { java.util.zip.GZIPInputStream(context.assets.open("studios/${studio.id}.json.gz")) }
                    input.bufferedReader().use { reader ->
                        gson.fromJson<List<MooncatTitle>>(reader, type)
                            .filter { it.imdb.matches(Regex("tt[0-9]+")) }.distinctBy { it.imdb }
                    }
                }
            }
        }
        catalogues = loaded.mapValues { it.value.getOrDefault(emptyList()) }
        failed = loaded.filterValues { it.isFailure }.keys
        loading = false
    }
    LaunchedEffect(loading, selectedId, filter) {
        if (!loading) {
            withFrameNanos { }
            runCatching { initialFocus.requestFocus() }
        }
    }
    BackHandler(selectedId != null) { selectedId = null }
    val selected = mooncatStudios.firstOrNull { it.id == selectedId }
    val catalogue = remember(catalogues, selectedId, filter) {
        catalogues[selectedId].orEmpty().filter { filter == "all" || it.type == filter }
    }
    val spacing = NuvioTheme.spacing
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = spacing.lg)) {
            Text(selected?.name ?: stringResource(R.string.mooncat_studios_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                if (selected == null) stringResource(R.string.mooncat_studios_subtitle)
                else stringResource(R.string.mooncat_studios_title_count, catalogue.size),
                color = NuvioTheme.extendedColors.textSecondary, modifier = Modifier.padding(top = spacing.sm)
            )
        }
        if (loading) {
            Text(stringResource(R.string.mooncat_studios_loading), Modifier.padding(32.dp))
        } else if (selectedId in failed || failed.size == mooncatStudios.size) {
            Text(stringResource(R.string.mooncat_studios_error), Modifier.padding(32.dp))
            Button(onClick = { retry++ }, modifier = Modifier.padding(horizontal = 32.dp).focusRequester(initialFocus)) {
                Text(stringResource(R.string.mooncat_studios_retry))
            }
        } else if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg), contentPadding = PaddingValues(bottom = 40.dp)) {
                item(key = "studio-picker") {
                    LazyRow(modifier = Modifier.focusRestorer().focusGroup(),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        items(mooncatStudios, key = { it.id }) { studio ->
                            Card(onClick = { selectedId = studio.id },
                                modifier = Modifier.size(168.dp, 110.dp).semantics { contentDescription = studio.name }.then(if (studio == mooncatStudios.first()) Modifier.focusRequester(initialFocus) else Modifier),
                                colors = CardDefaults.colors(containerColor = NuvioTheme.extendedColors.backgroundCard,
                                    focusedContainerColor = MaterialTheme.colorScheme.primary),
                                border = CardDefaults.border(focusedBorder = Border(
                                    border = NuvioTheme.focusRing.border(spacing.xxs), shape = RoundedCornerShape(12.dp))),
                                scale = CardDefaults.scale(focusedScale = 1.05f)) {
                                Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Image(painterResource(studio.logo), null,
                                        Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                                    Text(studio.name, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                items(mooncatStudios, key = { it.id }) { studio ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(studio.name, style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { selectedId = studio.id }) {
                                Text(stringResource(R.string.mooncat_studios_browse, catalogues[studio.id].orEmpty().size))
                            }
                        }
                        LazyRow(modifier = Modifier.focusRestorer().focusGroup(), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            items(catalogues[studio.id].orEmpty().take(16), key = { it.imdb }) { work -> StudioPoster(work, onOpen) }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(horizontal = 32.dp, vertical = spacing.sm).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Button(onClick = { selectedId = null }) { Text(stringResource(R.string.mooncat_studios_all)) }
                listOf("all" to R.string.mooncat_studios_all_titles, "movie" to R.string.mooncat_studios_movies,
                    "series" to R.string.mooncat_studios_series).forEach { (value, label) ->
                    Button(onClick = { filter = value },
                        modifier = if (value == filter) Modifier.focusRequester(initialFocus) else Modifier,
                        colors = ButtonDefaults.colors(
                            containerColor = if (filter == value) MaterialTheme.colorScheme.primary else NuvioTheme.extendedColors.backgroundCard,
                            contentColor = if (filter == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) {
                        Text(stringResource(label))
                    }
                }
            }
            key(selectedId, filter) {
                if (catalogue.isEmpty()) Text(stringResource(R.string.mooncat_studios_empty), Modifier.padding(32.dp),
                    color = NuvioTheme.extendedColors.textSecondary)
                // A lazy grid keeps TV focus scrolling and only composes the visible posters.
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer().focusGroup()) {
                    items(catalogue, key = { it.imdb }) { work -> StudioPoster(work, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun StudioPoster(work: MooncatTitle, onOpen: (MooncatTitle) -> Unit) {
    val preview = remember(work) { work.preview() }
    ContentCard(item = preview, posterCardStyle = PosterCardStyle(width = 118.dp, height = 177.dp),
        showImdbRatings = false, onClick = { onOpen(work) })
}
