package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Dispatcher
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Races bounded HTTP 64 KiB sample probes, never multiple torrent engines or whole media downloads.
 * A responsive endpoint is not a guarantee of sustained playback speed or a valid media container.
 */
object MoonCatSourceRace {
    const val MAX_CANDIDATES = 8
    private val client = OkHttpClient.Builder().dispatcher(Dispatcher().apply {
        maxRequests = MAX_CANDIDATES
        maxRequestsPerHost = MAX_CANDIDATES
    }).callTimeout(1500, TimeUnit.MILLISECONDS)
        .connectTimeout(1000, TimeUnit.MILLISECONDS).readTimeout(1000, TimeUnit.MILLISECONDS)
        .followSslRedirects(false).build()
    // Addon headers may contain vendor-specific credentials; never redirect those requests.
    private val credentialClient = client.newBuilder().followRedirects(false).build()

    suspend fun choose(candidates: List<Stream>, preferredLanguage: String = "hi"): Stream? = coroutineScope {
        val ranked = MoonCatSourcePolicy.playbackCandidates(candidates, preferredLanguage).distinctBy {
            it.getStreamUrl() ?: "${it.getEffectiveInfoHash()}:${it.getEffectiveFileIdx()}"
        }.take(MAX_CANDIDATES)
        val results = Channel<Pair<Stream, Sample>?>(Channel.UNLIMITED)
        val jobs = ranked.mapNotNull { stream ->
            val url = stream.getStreamUrl()?.takeIf {
                it.startsWith("https://", true) || it.startsWith("http://", true)
            } ?: return@mapNotNull null
            launch { results.send(probe(stream, url)?.let { sample -> stream to sample }) }
        }
        val samples = mutableListOf<Pair<Stream, Sample>>()
        try {
            repeat(jobs.size) {
                val result = results.receive()
                if (result != null) {
                    samples.add(result)
                }
            }
            selectMeasured(samples.map { (stream, sample) -> stream to sample.bytesPerSecond }, preferredLanguage)
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    /** A short sample is a readiness hint. Prefer HD with headroom, then rank language/quality. */
    internal fun selectMeasured(samples: List<Pair<Stream, Double>>, language: String = "hi"): Stream? {
        val usable = samples.filter { (stream, speed) ->
            MoonCatSourcePolicy.suitable(stream) && speed.isFinite() && speed > 0
        }
        val withHeadroom = usable.filter { (stream, speed) ->
            speed >= if (MoonCatSourcePolicy.qualityTier(stream) == 0) 1_000_000.0 else 500_000.0
        }
        return (withHeadroom.ifEmpty { usable }).minWithOrNull(
            compareBy<Pair<Stream, Double>> { MoonCatSourcePolicy.languageTier(it.first, language) }
                .thenBy { MoonCatSourcePolicy.qualityTier(it.first) }
                .thenBy { MoonCatSourcePolicy.releaseTier(it.first) }
                .thenByDescending { it.second }
        )?.first
    }

    internal fun validContentRange(header: String?, contentLength: Long): Long? {
        val match = header?.let { Regex("bytes 0-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE).matchEntire(it.trim()) }
            ?: return null
        val end = match.groupValues[1].toLongOrNull() ?: return null
        val total = match.groupValues[2].toLongOrNull() ?: return null
        val expected = end + 1
        return expected.takeIf { end in 0..65535 && total > end && (contentLength < 0 || contentLength == expected) }
    }

    private data class Sample(val bytes: Int, val elapsedNanos: Long, val playlist: String? = null) {
        val bytesPerSecond: Double get() = bytes * 1_000_000_000.0 / elapsedNanos.coerceAtLeast(1)
    }

    private suspend fun probe(stream: Stream, url: String): Sample? = withTimeoutOrNull(3000L) {
        var currentUrl = url
        repeat(3) {
            val sample = probeResource(stream, currentUrl) ?: return@withTimeoutOrNull null
            val playlist = sample.playlist ?: return@withTimeoutOrNull sample
            currentUrl = playlistTarget(playlist, currentUrl) ?: return@withTimeoutOrNull null
        }
        null
    }

    /** Sample an actual HLS media segment, rather than treating a tiny playlist as bandwidth. */
    internal fun playlistTarget(playlist: String, baseUrl: String): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val lines = playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != "#EXTM3U") return null
        val variants = lines.indices.filter { lines[it].startsWith("#EXT-X-STREAM-INF:") }
        if (variants.isNotEmpty()) {
            return variants.mapNotNull { index ->
                val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE).find(lines[index])
                val width = resolution?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val height = resolution?.groupValues?.get(2)?.toIntOrNull() ?: 0
                if (width > 1920 || height > 1080) return@mapNotNull null
                val uri = lines.getOrNull(index + 1)?.takeUnless { it.startsWith("#") } ?: return@mapNotNull null
                base.resolve(uri)?.takeIf { it.isHttps || !base.isHttps }?.toString()?.let { height to it }
            }.maxByOrNull { it.first }?.second
        }
        val index = lines.indexOfFirst { it.startsWith("#EXTINF:") }
        if (index < 0) return null
        val uri = lines.drop(index + 1).firstOrNull { !it.startsWith("#") } ?: return null
        return base.resolve(uri)?.takeIf { it.isHttps || !base.isHttps }?.toString()
    }

    private suspend fun probeResource(stream: Stream, url: String): Sample? = suspendCancellableCoroutine { continuation ->
        val canSendHeaders = url.toHttpUrlOrNull()?.host == stream.getStreamUrl()?.toHttpUrlOrNull()?.host
        val request = runCatching {
            Request.Builder().url(url).apply {
                // Never transmit addon credentials over plaintext HTTP.
                if (url.startsWith("https://", true) && canSendHeaders) {
                    stream.behaviorHints?.proxyHeaders?.request?.forEach { (key, value) -> header(key, value) }
                }
                header("Accept-Encoding", "identity")
                header("Range", "bytes=0-65535")
            }.build()
        }.getOrNull()
        if (request == null) { continuation.resume(null); return@suspendCancellableCoroutine }
        val hasAddonHeaders = url.startsWith("https://", true) && canSendHeaders && !stream.behaviorHints?.proxyHeaders?.request.isNullOrEmpty()
        val call = (if (hasAddonHeaders) credentialClient else client).newCall(request)
        val started = System.nanoTime()
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val sample = runCatching {
                    response.use {
                        val type = it.header("Content-Type").orEmpty().lowercase()
                        val body = it.body
                        val range = it.header("Content-Range")
                        val expectedRangeBytes = if (it.code == 206) validContentRange(range, body?.contentLength() ?: -1L) else null
                        val invalidRange = it.code == 206 && expectedRangeBytes == null
                        if (!it.isSuccessful || invalidRange || type.contains("text/html") || type.contains("application/json") || body == null) {
                            null
                        } else {
                            // A server may ignore Range; never read more than the fixed sample.
                            val input = body.byteStream()
                            val buffer = ByteArray(8192)
                            val captured = java.io.ByteArrayOutputStream()
                            var total = 0
                            while (total < 65536) {
                                val read = input.read(buffer, 0, minOf(buffer.size, 65536 - total))
                                if (read <= 0) break
                                captured.write(buffer, 0, read)
                                total += read
                            }
                            if (total == 0 || (expectedRangeBytes != null && total.toLong() != expectedRangeBytes)) null
                            else {
                                val prefix = captured.toByteArray()
                                val isPlaylist = prefix.take(7).toByteArray().toString(Charsets.UTF_8) == "#EXTM3U"
                                Sample(total, System.nanoTime() - started,
                                    if (isPlaylist) prefix.toString(Charsets.UTF_8) else null)
                            }
                        }
                    }
                }.getOrNull()
                if (continuation.isActive) continuation.resume(sample)
            }
        })
    }
}
