package com.nuvio.tv.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class MooncatTitle(val title: String, val imdb: String, val type: String = "movie")
data class MooncatStudio(val name: String, val works: List<MooncatTitle>)
private fun movie(title: String, imdb: String) = MooncatTitle(title, imdb)
private fun series(title: String, imdb: String) = MooncatTitle(title, imdb, "series")
val mooncatStudios = listOf(
    MooncatStudio("Marvel", listOf(movie("Iron Man", "tt0371746"), movie("Avengers: Endgame", "tt4154796"),movie("Black Panther", "tt1825683"),series("Loki", "tt9140554"),series("WandaVision","tt9140560"))),
    MooncatStudio("DC", listOf(movie("The Dark Knight","tt0468569"),movie("Man of Steel","tt0770828"), movie("The Batman","tt1877830"),series("Peacemaker","tt13146488"),series("The Penguin","tt15435876"))),
    MooncatStudio("Wizarding World", listOf(movie("Harry Potter and the Philosopher's Stone","tt0241527"),movie("Harry Potter and the Chamber of Secrets","tt0295297"),movie("Harry Potter and the Prisoner of Azkaban","tt0304141"),movie("Fantastic Beasts and Where to Find Them","tt3183660"))),
    MooncatStudio("HBO", listOf(series("Game of Thrones","tt0944947"),series("The Last of Us","tt3581920"),series("Succession","tt7660850"),series("House of the Dragon","tt11198330"))),
    MooncatStudio("Paramount", listOf(movie("Top Gun: Maverick","tt1745960"),movie("Mission: Impossible – Fallout","tt4912910"),movie("Transformers","tt0418279"),series("Star Trek: Strange New Worlds","tt12327578"))),
    MooncatStudio("Star Wars", listOf(movie("Star Wars: A New Hope","tt0076759"),movie("The Empire Strikes Back","tt0080684"),series("The Mandalorian","tt8111088"),series("Andor","tt9253284"))),
    MooncatStudio("Pixar", listOf(movie("Toy Story","tt0114709"),movie("Inside Out","tt2096673"),movie("Coco","tt2380307"),movie("WALL-E","tt0910970"))),
    MooncatStudio("DreamWorks", listOf(movie("Shrek","tt0126029"),movie("Kung Fu Panda","tt0441773"),movie("How to Train Your Dragon","tt0892769"))),
    MooncatStudio("Disney", listOf(movie("The Lion King","tt0110357"),movie("Frozen","tt2294629"),series("The Book of Boba Fett","tt13668894"))),
    MooncatStudio("Universal", listOf(movie("Jurassic Park","tt0107290"),movie("Oppenheimer","tt15398776"),movie("Jaws","tt0073195"))),
    MooncatStudio("Sony", listOf(movie("Spider-Man: Into the Spider-Verse","tt4633694"),movie("Venom","tt1270797"),movie("Jumanji: Welcome to the Jungle","tt2283362")))
)

@Composable
fun MooncatStudiosScreen(onOpen: (MooncatTitle) -> Unit) {
    var selected by remember { mutableStateOf<MooncatStudio?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090E1F)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text("Studios & Collections", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text("Explore films and series by franchise or studio", color = Color(0xFFB5C2E3))
        if (selected != null) {
            Button(onClick = { selected = null }) { Text("‹ All collections") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                items(mooncatStudios) { studio ->
                    Button(onClick = { selected = studio }, modifier = Modifier.fillMaxWidth()) {
                        Text(studio.name + "   ·   " + studio.works.size + " featured titles")
                    }
                }
            } else {
                items(selected!!.works) { work ->
                    Button(onClick = { onOpen(work) }, modifier = Modifier.fillMaxWidth()) {
                        Text(work.title + if (work.type == "series") "  ·  Series" else "  ·  Movie")
                    }
                }
            }
        }
    }
}

