package com.nuvio.tv.updater

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ApkDownloaderTest {
    private fun tempFile(): File = Files.createTempDirectory("mooncat-update-test").resolve("update.apk").toFile()

    @Test fun `complete APK is validated and progress is throttled with final count`() = runBlocking {
        MockWebServer().use { server ->
            val bytes = ByteArray(2 * 1024 * 1024) { 42 }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val destination = tempFile()
            try {
                val progress = mutableListOf<Pair<Long, Long?>>()
                val result = ApkDownloader(OkHttpClient()).download(server.url("/app.apk").toString(),
                    destination, { read, total -> progress += read to total }, bytes.size.toLong())
                assertTrue(result.isSuccess)
                assertArrayEquals(bytes, destination.readBytes())
                assertEquals(0L to bytes.size.toLong(), progress.first())
                assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
                assertTrue("progress must not update for every 8 KB", progress.size < 30)
            } finally { destination.parentFile?.deleteRecursively() }
        }
    }

    @Test fun `mismatched release size cannot become an installable APK`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("short"))
            val destination = tempFile()
            try {
                val result = ApkDownloader(OkHttpClient()).download(server.url("/app.apk").toString(),
                    destination, { _, _ -> }, expectedSizeBytes = 100)
                assertTrue(result.isFailure)
                assertFalse(destination.exists())
            } finally { destination.parentFile?.deleteRecursively() }
        }
    }

    @Test fun `disconnect removes partial download`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(100_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val destination = tempFile()
            try {
                val result = ApkDownloader(OkHttpClient()).download(server.url("/app.apk").toString(),
                    destination, { _, _ -> })
                assertTrue(result.isFailure)
                assertFalse(destination.exists())
            } finally { destination.parentFile?.deleteRecursively() }
        }
    }

    @Test fun `cancel closes outstanding request without waiting for read timeout`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
            val destination = tempFile()
            try {
                val job = async { ApkDownloader(client).download(server.url("/app.apk").toString(), destination, { _, _ -> }) }
                // Wait for the real HTTP request, rather than sleeping to guess when it started.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                }
                withTimeout(1_000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
                assertFalse(destination.exists())
            } finally { destination.parentFile?.deleteRecursively() }
        }
    }
}
