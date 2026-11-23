package dev.mooncat.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import com.nuvio.tv.core.torrent.DefaultTorrentTrackers
import com.nuvio.tv.core.torrent.buildMagnetUri
import java.io.File
import java.net.URI
import java.util.function.Consumer
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal class MobileTorrentPlayback(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var request: Job? = null
    private var generation = 0L

    fun open(route: JSONObject, onReady: Consumer<String>, onFailure: Runnable) {
        close()
        val current = generation
        fun deliver(action: () -> Unit) {
            main.post { if (generation == current) action() }
        }
        request = scope.launch {
            engineLock.withLock {
                var engine: NuvioEngine? = null
                try {
                    val url = withTimeout(25_000L) {
                        val source = parseRoute(route)
                        val cache = File(context.cacheDir, "nuvio-engine")
                        val state = File(context.noBackupFilesDir, "nuvio-engine/state")
                        check(cache.mkdirs() || cache.isDirectory)
                        check(state.mkdirs() || state.isDirectory)
                        val available = cache.usableSpace
                        val diskBudget = min(
                            1_000_000_000L,
                            min(available / 5, (available - 1_073_741_824L).coerceAtLeast(0L))
                        )
                        val opened = NuvioEngine.create(
                            NuvioEngineConfig(
                                dataDirectory = state,
                                cacheDirectory = cache,
                                diskCacheCapacityBytes = diskBudget,
                                torrentProfile = NuvioTorrentProfile.Balanced,
                                uploadMode = NuvioUploadMode.Disabled,
                                streamInactivityTimeoutMilliseconds = 0
                            )
                        )
                        engine = opened
                        val id = opened.addMagnet(buildMagnetUri(source.hash, DefaultTorrentTrackers + source.trackers))
                        val stream = opened.prepareStream(id, source.fileIndex, source.filename)
                        require(stream.fileSize in 1..MAX_BYTES)
                        require(source.fileIndex == null || source.fileIndex == stream.fileIndex)
                        checkedLoopbackUrl(stream.url)
                    }
                    deliver { onReady.accept(url) }
                    awaitCancellation()
                } catch (_: TimeoutCancellationException) {
                    Log.w(TAG, "Torrent metadata timed out")
                    deliver(onFailure::run)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Torrent source could not start (${error.javaClass.simpleName})")
                    deliver(onFailure::run)
                } catch (error: LinkageError) {
                    Log.w(TAG, "Torrent engine could not load (${error.javaClass.simpleName})")
                    deliver(onFailure::run)
                } finally {
                    // Closing the engine also stops any unresolved native command or prepared stream.
                    runCatching { engine?.close() }
                }
            }
        }
    }

    fun close() {
        generation++
        request?.cancel()
        request = null
    }

    private data class Route(val hash: String, val fileIndex: Int?, val filename: String?, val trackers: List<String>)

    private fun parseRoute(route: JSONObject): Route {
        val index = route.opt("fileIndex")?.takeUnless { it === JSONObject.NULL }
        require(index == null || (index is Int && index in 0..65535))
        val filename = route.optString("filename").takeIf { it.isNotBlank() && it.length <= 512 }
        val rawTrackers = route.optJSONArray("trackers")
        val trackers = (0 until min(20, rawTrackers?.length() ?: 0)).mapNotNull { position ->
            val value = rawTrackers?.optString(position).orEmpty()
            value.takeIf { it.length <= 512 && trackerPattern.containsMatchIn(it) }
        }
        return Route(route.getString("infoHash"), index as Int?, filename, trackers)
    }

    private fun checkedLoopbackUrl(value: String): String {
        val url = URI(value)
        require(url.scheme == "http" && (url.host == "127.0.0.1" || url.host == "localhost") &&
            url.port in 1..65535 && url.userInfo == null && url.fragment == null)
        return value
    }

    private companion object {
        const val TAG = "MoonCatTorrent"
        const val MAX_BYTES = 6_000_000_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engineLock = Mutex()
        val trackerPattern = Regex("^(?:udp|https?)://[^\\s]+$", RegexOption.IGNORE_CASE)
    }
}
