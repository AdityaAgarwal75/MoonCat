package com.nuvio.tv.core.player

/** The selected decoder tracks must belong to the requested route, not just the same torrent. */
internal fun audioPlaybackRequestMatches(
    candidateUrl: String?, candidateHash: String?, candidateFileIndex: Int?,
    candidateHeaders: Map<String, String>, observedUrl: String?, observedHash: String?,
    observedFileIndex: Int?, observedHeaders: Map<String, String>
): Boolean {
    if (!candidateUrl.isNullOrBlank()) {
        return !observedUrl.isNullOrBlank() &&
            playbackRequestKey(candidateUrl, null, null, candidateHeaders) ==
            playbackRequestKey(observedUrl, null, null, observedHeaders)
    }
    return !candidateHash.isNullOrBlank() && candidateHash.equals(observedHash, ignoreCase = true) &&
        (candidateFileIndex == null || candidateFileIndex == observedFileIndex)
}
