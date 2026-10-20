package com.nuvio.tv.ui.screens.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class MooncatChannel(val name: String, val language: String, val url: String)
val mooncatNewsChannels = listOf(
    MooncatChannel("DD News", "Hindi", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/0811cd8c37ca4c409d5385a6cd2fa18b/index.m3u8"),
    MooncatChannel("Aaj Tak", "Hindi", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8"),
    MooncatChannel("ABP News", "Hindi", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8"),
    MooncatChannel("India Today", "English", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-293160/master.m3u8")
)

@Composable
fun MooncatNewsScreen(onPlay: (MooncatChannel) -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080D1F)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Live News", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text("Choose a live broadcaster. Streams open in the MoonCat player.", color = Color(0xFFBCC7E2))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mooncatNewsChannels) { channel ->
                Button(onClick = { onPlay(channel) }, modifier = Modifier.fillMaxWidth()) {
                    Text(channel.name + "  ·  " + channel.language + "  ·  LIVE")
                }
            }
        }
    }
}

