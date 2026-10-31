package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Addon
import java.net.URI
import java.util.Locale

/** Query configured addons, preserving the household's explicit TorrentsDB exclusion. */
object MoonCatStreamProviders {
    fun isAllowed(addon: Addon): Boolean {
        if (!addon.enabled) return false
        val uri = runCatching { URI(addon.baseUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val id = addon.id.lowercase(Locale.ROOT)
        if (host == "torrentsdb.com" || host.endsWith(".torrentsdb.com") ||
            id.contains("torrentsdb")) return false
        // Remote addons use TLS. Local self-hosted development can use loopback HTTP.
        return uri.scheme.equals("https", true) ||
            (uri.scheme.equals("http", true) && host in setOf("localhost", "127.0.0.1", "[::1]"))
    }
}
