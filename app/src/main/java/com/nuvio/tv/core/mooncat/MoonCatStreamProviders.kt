package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Addon
import java.net.URI
import java.util.Locale

/** Limits movie/episode streams without removing metadata, subtitles or saved addon settings. */
object MoonCatStreamProviders {
    fun isAllowed(addon: Addon): Boolean {
        val host = runCatching { URI(addon.baseUrl).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val id = addon.id.lowercase(Locale.ROOT)
        return host == "torrentio.strem.fun" || host.endsWith(".torrentio.strem.fun") ||
            host == "torrentsdb.com" || host.endsWith(".torrentsdb.com") ||
            id == "com.stremio.torrentio" || id == "mooncat.provider.torrentsdb"
    }
}
