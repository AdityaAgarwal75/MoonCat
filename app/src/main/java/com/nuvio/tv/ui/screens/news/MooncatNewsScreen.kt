package com.nuvio.tv.ui.screens.news

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme

/** Small first-party directory. Web broadcasts are never misrepresented as raw media URLs. */
@Composable
fun MooncatNewsScreen() {
    val context = LocalContext.current
    val channels = listOf(
        Triple("DD News", "Hindi · official broadcaster", "https://ddnews.gov.in/en/"),
        Triple("Aaj Tak", "Hindi · official live page", "https://www.aajtak.in/livetv"),
        Triple("ABP News", "Hindi · official live page", "https://www.abplive.com/live-tv"),
        Triple("India Today", "English · official live page", "https://www.indiatoday.in/livetv")
    )
    LazyColumn(modifier = Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("News", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Your essential channels. Official broadcasts open in their supported app or browser.") }
        items(channels) { channel ->
            Button(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.third))) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "Install a browser or the broadcaster's app to open this channel.", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(channel.first, style = MaterialTheme.typography.titleLarge)
                    Text(channel.second)
                }
            }
        }
        item { Text("In-app live playback is available through an authorized live-TV add-on. These official pages are not advertised as verified HLS streams.") }
    }
}
