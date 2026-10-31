package com.nuvio.tv.core.torrent

/** Exact file request identity. A null file index must never alias a specific episode. */
internal data class PreparedTorrentRequest(val hash: String, val fileIndex: Int?, val filename: String?) {
    fun canReuse(request: PreparedTorrentRequest, preparedAtMs: Long, nowMs: Long): Boolean =
        this == request && nowMs >= preparedAtMs && nowMs - preparedAtMs < TTL_MS

    companion object { const val TTL_MS = 15_000L }
}
