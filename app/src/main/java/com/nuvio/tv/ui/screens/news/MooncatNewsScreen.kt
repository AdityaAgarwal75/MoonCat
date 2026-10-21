@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.nuvio.tv.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class MooncatChannel(val name: String, val language: String, val url: String, val logo: Int, val headers: Map<String, String> = emptyMap())
val mooncatNewsChannels = listOf(
    MooncatChannel("DD News", "Hindi", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/0811cd8c37ca4c409d5385a6cd2fa18b/index.m3u8", R.drawable.channel_ddnews, emptyMap()),
    MooncatChannel("Aaj Tak", "Hindi", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8", R.drawable.channel_aajtak, emptyMap()),
    MooncatChannel("ABP News", "Hindi", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8", R.drawable.channel_abpnews, emptyMap()),
    MooncatChannel("India TV", "Hindi", "https://pl-indiatvnews.akamaized.net/out/v1/db79179b608641ceaa5a4d0dd0dca8da/index.m3u8", R.drawable.channel_indiatv, emptyMap()),
    MooncatChannel("Zee News", "Hindi", "https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8", R.drawable.channel_zeenews, emptyMap()),
    MooncatChannel("News18 India", "Hindi", "https://n18syndication.akamaized.net/bpk-tv/News18_India_NW18_MOB/output01/master.m3u8", R.drawable.channel_news18india, emptyMap()),
    MooncatChannel("TV9 Bharatvarsh", "Hindi", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9hinjzgtpe/liveabr/playlist.m3u8", R.drawable.channel_tv9bharatvarsh, emptyMap()),
    MooncatChannel("News24", "Hindi", "https://news24cdn.vidgyor.com/news24-origin/liveabr/playlist.m3u8", R.drawable.channel_news24, emptyMap()),
    MooncatChannel("Times Now Navbharat", "Hindi", "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/YuppTV/TimesNowNavbharat.m3u8", R.drawable.channel_timesnownavbharat, emptyMap()),
    MooncatChannel("Bharat24", "Hindi", "https://cdn.ottlive.co.in/bharat24/index.m3u8", R.drawable.channel_bharat24, emptyMap()),
    MooncatChannel("Good News Today", "Hindi", "https://aajtaklive.vgcdn.net/v1/master/611d79b11b77e2f571934fd80ca1413453772ac7/3196cced-ce29-4219-9809-f07ccdaa02b9/vglive-sk-848805/master.m3u8", R.drawable.channel_goodnewstoday, emptyMap()),
    MooncatChannel("India Today", "English", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-293160/master.m3u8", R.drawable.channel_indiatoday, emptyMap()),
    MooncatChannel("Times Now", "English", "https://dztlhgid9me95.cloudfront.net/live-tv/Vidgyor/timesnow/timesnow_master.m3u8", R.drawable.channel_timesnow, mapOf("Referer" to "https://www.timesnownews.com/")),
    MooncatChannel("CNN News18", "English", "https://n18syndication.akamaized.net/bpk-tv/CNN_News18_NW18_MOB/output01/master.m3u8", R.drawable.channel_cnnnews18, emptyMap()),
    MooncatChannel("CNBC TV18", "English", "https://n18syndication.akamaized.net/bpk-tv/CNBC_TV18_NW18_MOB/output01/index.m3u8", R.drawable.channel_cnbctv18, emptyMap()),
    MooncatChannel("CNBC Awaaz", "Hindi", "https://n18syndication.akamaized.net/bpk-tv/CNBC_Awaaz_NW18_MOB/output01/master.m3u8", R.drawable.channel_cnbcawaaz, emptyMap()),
    MooncatChannel("ABP Ananda", "Bengali", "https://d2l4ar6y3mrs4k.cloudfront.net/live-streaming/ananda-livetv/master.m3u8", R.drawable.channel_abpananda, emptyMap()),
    MooncatChannel("ABP Asmita", "Gujarati", "https://d2l4ar6y3mrs4k.cloudfront.net/live-streaming/asmita-livetv/master.m3u8", R.drawable.channel_abpasmita, emptyMap()),
    MooncatChannel("ABP Ganga", "Hindi", "https://d2l4ar6y3mrs4k.cloudfront.net/live-streaming/ganga-livetv/master.m3u8", R.drawable.channel_abpganga, emptyMap()),
    MooncatChannel("TV9 Marathi", "Marathi", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9marlygv8h/liveabr/playlist.m3u8", R.drawable.channel_tv9marathi, emptyMap())
)

@Composable
fun MooncatNewsScreen(onPlay: (MooncatChannel) -> Unit = {}) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var opening by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var openJob by remember { mutableStateOf<Job?>(null) }
    val client = remember { OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build() }
    fun openChannel(channel: MooncatChannel) {
        // Rapid remote presses must not launch an earlier channel after the latest choice.
        openJob?.cancel()
        openJob = scope.launch {
            opening = channel.name
            failed = null
            val available = suspendCancellableCoroutine<Boolean> { continuation ->
                val request = Request.Builder().url(channel.url).header("User-Agent", "Mozilla/5.0").apply {
                    channel.headers.forEach { (key, value) -> header(key, value) }
                }.build()
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val valid = runCatching {
                            response.use {
                                if (!it.isSuccessful) return@use false
                                val source = it.body?.source() ?: return@use false
                                source.request(16)
                                val count = minOf(source.buffer.size, 256L)
                                source.buffer.clone().readUtf8(count)
                                    .trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U")
                            }
                        }.getOrDefault(false)
                        if (continuation.isActive) continuation.resume(valid)
                    }
                })
            }
            opening = null
            if (available) onPlay(channel) else failed = channel.name
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF080B14)).padding(horizontal = 32.dp)) {
        Text("Live News", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 24.dp))
        Text("20 Indian broadcasters  ·  Watch inside MoonCat", color = Color(0xFFA8B0C4), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            listOf("All", "Hindi", "English", "Regional").forEach { language ->
                Button(onClick = { filter = language }) { Text(if (filter == language) "● $language" else language) }
            }
        }
        if (opening != null) Text("Connecting to $opening…", color = Color(0xFFB8B2FF), modifier = Modifier.padding(bottom = 8.dp))
        if (failed != null) Text("$failed is unavailable right now. Select it to retry, or choose another channel.",
            color = Color(0xFFFF9BA7), modifier = Modifier.padding(bottom = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(mooncatNewsChannels.filter { filter == "All" || it.language == filter || (filter == "Regional" && it.language !in listOf("Hindi", "English")) }, key = { it.name }) { channel ->
                Card(onClick = { openChannel(channel) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF151B2A))) {
                        Image(painterResource(channel.logo), channel.name,
                            Modifier.fillMaxWidth().height(92.dp).background(Color(0xFFF4F4F6)).padding(16.dp), contentScale = ContentScale.Fit)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(channel.name, style = MaterialTheme.typography.titleSmall)
                                Text(channel.language, color = Color(0xFFA8B0C4), style = MaterialTheme.typography.bodySmall)
                            }
                            Text("● LIVE", color = Color(0xFFFF6B7B), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
