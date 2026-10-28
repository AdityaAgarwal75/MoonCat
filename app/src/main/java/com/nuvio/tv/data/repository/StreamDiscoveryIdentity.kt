package com.nuvio.tv.data.repository

import com.nuvio.tv.domain.model.Stream

/**
 * A playable request, rather than just a torrent hash. A cached HTTP URL and an
 * uncached torrent can describe the same file but have very different readiness.
 * Header-bearing streams must retain their request identity (Referer/auth etc.).
 * This key is memory-only and must never be logged or persisted.
 */
internal data class StreamDiscoveryIdentity(
    val url: String?,
    val externalUrl: String?,
    val youtubeId: String?,
    val hash: String?,
    val fileIndex: Int?,
    val serverKey: String?,
    val resolverService: String?,
    val resolverIndex: Int?,
    val requestHeaders: Map<String, String>,
    val filename: String?,
    val label: List<String?>
)

internal fun Stream.discoveryIdentity() = StreamDiscoveryIdentity(
    url = url,
    externalUrl = externalUrl,
    youtubeId = ytId,
    hash = getEffectiveInfoHash()?.lowercase(java.util.Locale.ROOT),
    fileIndex = getEffectiveFileIdx(),
    serverKey = serverTarget?.key(),
    resolverService = clientResolve?.service,
    resolverIndex = clientResolve?.serviceIndex,
    requestHeaders = behaviorHints?.proxyHeaders?.request.orEmpty()
        .mapKeys { (key, _) -> key.lowercase(java.util.Locale.ROOT) },
    filename = behaviorHints?.filename ?: clientResolve?.filename,
    // Provider labels may carry different audio claims for one shared endpoint.
    // Preserve that evidence until the player discovers the actual audio tracks.
    label = listOf(name, title, description)
)
