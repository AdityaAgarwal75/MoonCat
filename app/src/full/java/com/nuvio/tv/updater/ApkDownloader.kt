package com.nuvio.tv.updater

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ApkDownloader @Inject constructor(private val okHttpClient: OkHttpClient) {
    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        expectedSizeBytes: Long? = null
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val call = try {
            destinationFile.parentFile?.mkdirs()
            destinationFile.delete()
            okHttpClient.newCall(Request.Builder().url(url).build())
        } catch (error: Exception) {
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            call.cancel()
            destinationFile.delete()
        }
        // OkHttp owns the I/O thread; cancellation closes the socket even during a blocked read.
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                destinationFile.delete()
                if (continuation.isActive) continuation.resume(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful) { "Download failed: HTTP ${it.code}" }
                        val body = it.body ?: error("Empty download body")
                        val contentLength = body.contentLength().takeIf { size -> size > 0 }
                        val expected = expectedSizeBytes?.takeIf { size -> size > 0 }
                        check(expected == null || contentLength == null || expected == contentLength) {
                            "APK size does not match the release. Please check for updates again."
                        }
                        val total = expected ?: contentLength
                        var downloaded = 0L
                        var lastProgressAt = System.nanoTime()
                        onProgress(0L, total)
                        body.byteStream().use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (continuation.isActive) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    val now = System.nanoTime()
                                    // Thousands of StateFlow updates per APK used to interrupt TV focus frames.
                                    if (now - lastProgressAt >= 100_000_000L) {
                                        onProgress(downloaded, total)
                                        lastProgressAt = now
                                    }
                                }
                                output.flush()
                            }
                        }
                        check(continuation.isActive && downloaded > 0 && (total == null || downloaded == total)) {
                            "APK download was incomplete. Please retry."
                        }
                        onProgress(downloaded, total)
                        destinationFile
                    }
                }
                if (result.isFailure || !continuation.isActive) destinationFile.delete()
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
}
