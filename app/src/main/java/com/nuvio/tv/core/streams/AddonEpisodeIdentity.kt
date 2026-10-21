package com.nuvio.tv.core.streams

/** Standard Stremio series IDs need the episode suffix. Never rewrite addon-specific IDs. */
internal fun addonMediaType(type: String): String = when (type.lowercase()) {
    "tv", "show" -> "series"
    else -> type.lowercase()
}

internal fun addonVideoId(type: String, videoId: String, season: Int?, episode: Int?): String {
    if (addonMediaType(type) != "series" || season == null || episode == null || season < 0 || episode < 1) return videoId
    return if (imdbTitleId.matches(videoId)) "$videoId:$season:$episode" else videoId
}

private val imdbTitleId = Regex("tt[0-9]+")
