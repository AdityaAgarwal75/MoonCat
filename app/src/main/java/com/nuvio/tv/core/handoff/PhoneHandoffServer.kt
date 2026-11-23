package com.nuvio.tv.core.handoff

import fi.iki.elonen.NanoHTTPD
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal class PhoneHandoffServer(
    val host: String,
    port: Int,
    key: String,
    private val pair: () -> Boolean,
    private val play: (PhoneHandoff) -> Boolean
) : NanoHTTPD(host, port) {
    private val auth = PhoneHandoffAuthenticator(key)
    private var lastPlay = 0L

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri !in setOf("/pair", "/play")) {
            return reply(Response.Status.NOT_FOUND, "not_found")
        }
        if (!isPrivatePhoneHandoffAddress(session.remoteIpAddress) || !session.queryParameterString.isNullOrEmpty() ||
            session.headers.containsKey("transfer-encoding")
        ) return reply(Response.Status.FORBIDDEN, "invalid_request")

        val length = session.headers["content-length"]?.toIntOrNull()
        if (length == null || length !in 2..4096) return reply(Response.Status.BAD_REQUEST, "invalid_body")
        if (session.headers["content-type"]?.substringBefore(';') != "application/json") {
            return reply(Response.Status.UNSUPPORTED_MEDIA_TYPE, "invalid_body")
        }
        val raw = runCatching {
            val bytes = ByteArray(length)
            DataInputStream(session.inputStream).readFully(bytes)
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull() ?: return reply(Response.Status.BAD_REQUEST, "invalid_body")

        val authorized = auth.accept(
            session.uri, session.headers["x-mooncat-time"], session.headers["x-mooncat-nonce"],
            session.headers["x-mooncat-signature"], raw
        )
        if (!authorized) return reply(Response.Status.UNAUTHORIZED, "not_authorized")
        if (session.uri == "/pair") {
            if (raw != "{}") return reply(Response.Status.BAD_REQUEST, "invalid_body")
            return if (pair()) reply(Response.Status.OK, "paired")
            else reply(Response.Status.CONFLICT, "pairing_closed")
        }

        val command = parsePhoneHandoff(raw) ?: return reply(Response.Status.BAD_REQUEST, "invalid_title")
        if (!allowPlay()) return reply(Response.Status.TOO_MANY_REQUESTS, "try_again")
        return if (play(command)) reply(Response.Status.ACCEPTED, "accepted")
        else reply(Response.Status.SERVICE_UNAVAILABLE, "tv_unavailable")
    }

    @Synchronized
    private fun allowPlay(): Boolean {
        val now = System.nanoTime()
        if (lastPlay != 0L && TimeUnit.NANOSECONDS.toMillis(now - lastPlay) < 1_000L) return false
        lastPlay = now
        return true
    }

    private fun reply(code: Response.Status, status: String): Response =
        newFixedLengthResponse(code, "application/json", "{\"status\":\"$status\"}").apply {
            addHeader("Cache-Control", "no-store")
            closeConnection(true)
        }
}
