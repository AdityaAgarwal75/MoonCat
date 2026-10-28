package com.nuvio.tv.core.player

import com.nuvio.tv.domain.model.Stream
import java.security.MessageDigest
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.Locale

/** Request identity is independent of provider labels and keeps alternative CDNs/headers alive.
 * Hashing prevents keys from exposing signed URLs or authorization headers in diagnostics.
 */
fun playbackRequestKey(
    url: String?,
    hash: String?,
    fileIndex: Int?,
    headers: Map<String, String> = emptyMap()
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun field(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
    }
    if (!url.isNullOrBlank()) {
        field("http")
        val normalized = normalizeRequestIdentity(url, headers)
        field(normalized.first)
        normalized.second.entries.sortedBy { it.key.lowercase(Locale.ROOT) }.forEach {
            field(it.key.lowercase(Locale.ROOT))
            field(it.value)
        }
    } else {
        field("torrent")
        field(hash.orEmpty().lowercase(Locale.ROOT))
        field(fileIndex?.toString().orEmpty())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun Stream.playbackRequestKey(): String {
    val route = getStreamUrl() ?: serverTarget?.key() ?: ytId?.let { "youtube:$it" }
        ?: clientResolve?.takeIf { isDirectDebrid() }?.let {
            "debrid:${it.service}:${it.serviceIndex}:${getEffectiveInfoHash()}:${getEffectiveFileIdx()}"
        }
    return playbackRequestKey(route, getEffectiveInfoHash(), getEffectiveFileIdx(),
        behaviorHints?.proxyHeaders?.request.orEmpty())
}

/** Mirrors playback sanitization: URL userinfo becomes Basic auth, Range is player-owned. */
private fun normalizeRequestIdentity(url: String, headers: Map<String, String>): Pair<String, Map<String, String>> {
    val cleanHeaders = headers.entries.mapNotNull { (key, value) ->
        val name = key.trim().lowercase(Locale.ROOT)
        val text = value.trim()
        if (name.isBlank() || name == "range" || text.isBlank()) null else name to text
    }.toMap().toMutableMap()
    val uri = runCatching { URI(url) }.getOrNull() ?: return url to cleanHeaders
    val authority = uri.rawAuthority ?: return url to cleanHeaders
    val rawUserInfo = authority.substringBeforeLast('@', "").takeIf { it.isNotBlank() }
    val userInfo = uri.userInfo ?: rawUserInfo?.let {
        runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull()
    }
    if (userInfo.isNullOrBlank()) return url to cleanHeaders
    val cleanAuthority = authority.substringAfterLast('@')
    if (cleanAuthority == authority || cleanAuthority.isBlank() || uri.scheme.isNullOrBlank()) return url to cleanHeaders
    val cleanUrl = buildString {
        append(uri.scheme).append("://").append(cleanAuthority).append(uri.rawPath.orEmpty())
        uri.rawQuery?.let { append('?').append(it) }
        uri.rawFragment?.let { append('#').append(it) }
    }
    if ("authorization" !in cleanHeaders) {
        cleanHeaders["authorization"] = "Basic " + Base64.getEncoder().encodeToString(userInfo.toByteArray(Charsets.UTF_8))
    }
    return cleanUrl to cleanHeaders
}
