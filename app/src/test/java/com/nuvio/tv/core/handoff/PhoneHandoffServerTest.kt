package com.nuvio.tv.core.handoff

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneHandoffServerTest {
    private val key = "a".repeat(64)
    private val movie = """{"contentId":"tt0371746","videoId":"tt0371746","type":"movie","title":"Iron Man","positionMs":84712}"""

    @Test fun validatesHandshakeAndRoutesPlaybackWithPosition() {
        var paired = false
        var received: PhoneHandoff? = null
        val server = PhoneHandoffServer(
            "192.168.1.10", 57342, key,
            pair = { paired = true; true },
            play = { if (!paired) false else { received = it; true } }
        )
        assertEquals(Status.UNAUTHORIZED, server.serve(request("/pair", "{}", 1, "c".repeat(64))).status)
        assertEquals(Status.SERVICE_UNAVAILABLE, server.serve(request("/play", movie, 2)).status)
        assertEquals(Status.OK, server.serve(request("/pair", "{}", 3)).status)
        val accepted = request("/play", movie, 4)
        // The short throttle deliberately protects the TV from repeated sends during source lookup.
        val readyServer = PhoneHandoffServer(
            "192.168.1.10", 57342, key, pair = { paired },
            play = { if (!paired) false else { received = it; true } }
        )
        assertEquals(Status.ACCEPTED, readyServer.serve(accepted).status)
        assertEquals(Status.UNAUTHORIZED, readyServer.serve(request("/play", movie, 4)).status)
        assertEquals(84712L, received?.positionMs)
    }

    private fun request(uri: String, body: String, sequence: Int, keyForSignature: String = key): NanoHTTPD.IHTTPSession {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = sequence.toString().padStart(32, '0')
        val bytes = body.toByteArray()
        return object : NanoHTTPD.IHTTPSession {
            override fun execute() = error("Unused")
            override fun getCookies() = error("Unused")
            override fun getHeaders() = mapOf(
                "content-type" to "application/json",
                "content-length" to bytes.size.toString(),
                "x-mooncat-time" to timestamp,
                "x-mooncat-nonce" to nonce,
                "x-mooncat-signature" to signPhoneHandoff(keyForSignature, uri, timestamp, nonce, body)
            )
            override fun getInputStream() = ByteArrayInputStream(bytes)
            override fun getMethod() = NanoHTTPD.Method.POST
            override fun getParms() = emptyMap<String, String>()
            override fun getParameters() = emptyMap<String, List<String>>()
            override fun getQueryParameterString() = ""
            override fun getUri() = uri
            override fun parseBody(files: MutableMap<String, String>?) = error("Unused")
            override fun getRemoteIpAddress() = "192.168.1.12"
            override fun getRemoteHostName() = getRemoteIpAddress()
        }
    }
}
