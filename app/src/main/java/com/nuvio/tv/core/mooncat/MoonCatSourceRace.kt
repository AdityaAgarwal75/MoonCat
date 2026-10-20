package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Stream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
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
    const val MAX_CANDIDATES = 7
    private val client = OkHttpClient.Builder().dispatcher(Dispatcher().apply {
        maxRequests = MAX_CANDIDATES
        maxRequestsPerHost = MAX_CANDIDATES
    }).callTimeout(1500, TimeUnit.MILLISECONDS)
        .connectTimeout(1000, TimeUnit.MILLISECONDS).readTimeout(1000, TimeUnit.MILLISECONDS)
        .followSslRedirects(false).build()
    // Addon headers may contain vendor-specific credentials; never redirect those requests.
    private val credentialClient = client.newBuilder().followRedirects(false).build()

    suspend fun choose(candidates: List<Stream>): Stream? = coroutineScope {
        val ranked = MoonCatSourcePolicy.rank(candidates).distinctBy {
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
        val preferredTier = ranked.minOfOrNull(MoonCatSourcePolicy::qualityTier)
        try {
            repeat(jobs.size) {
                val result = results.receive()
                if (result != null) {
                    samples.add(result)
                    // A complete sample comfortably above typical 1080p bitrates can start early.
                    // This is a readiness threshold, not a sustained-bandwidth guarantee.
                    if (MoonCatSourcePolicy.qualityTier(result.first) == preferredTier &&
                        result.second.bytes == 65536 && result.second.bytesPerSecond >= 2_000_000.0) {
                        return@coroutineScope result.first
                    }
                }
            }
            samples
                .maxWithOrNull(compareBy<Pair<Stream, Sample>> { it.second.bytesPerSecond }
                    .thenBy { -it.second.elapsedNanos })?.first ?: ranked.firstOrNull { it.getStreamUrl() == null }
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    internal fun validContentRange(header: String?, contentLength: Long): Long? {
        val match = header?.let { Regex("bytes 0-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE).matchEntire(it.trim()) }
            ?: return null
        val end = match.groupValues[1].toLongOrNull() ?: return null
        val total = match.groupValues[2].toLongOrNull() ?: return null
        val expected = end + 1
        return expected.takeIf { end in 0..65535 && total > end && (contentLength < 0 || contentLength == expected) }
    }

    private data class Sample(val bytes: Int, val elapsedNanos: Long) {
        val bytesPerSecond: Double get() = bytes * 1_000_000_000.0 / elapsedNanos.coerceAtLeast(1)
    }

    private suspend fun probe(stream: Stream, url: String): Sample? = suspendCancellableCoroutine { continuation ->
        val request = runCatching {
            Request.Builder().url(url).apply {
                // Never transmit addon credentials over plaintext HTTP.
                if (url.startsWith("https://", true)) {
                    stream.behaviorHints?.proxyHeaders?.request?.forEach { (key, value) -> header(key, value) }
                }
                header("Accept-Encoding", "identity")
                header("Range", "bytes=0-65535")
            }.build()
        }.getOrNull()
        if (request == null) { continuation.resume(null); return@suspendCancellableCoroutine }
        val hasAddonHeaders = url.startsWith("https://", true) && !stream.behaviorHints?.proxyHeaders?.request.isNullOrEmpty()
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
                            var total = 0
                            while (total < 65536) {
                                val read = input.read(buffer, 0, minOf(buffer.size, 65536 - total))
                                if (read <= 0) break
                                total += read
                            }
                            if (total == 0 || (expectedRangeBytes != null && total.toLong() != expectedRangeBytes)) null
                            else Sample(total, System.nanoTime() - started)
                        }
                    }
                }.getOrNull()
                if (continuation.isActive) continuation.resume(sample)
            }
        })
    }
}
