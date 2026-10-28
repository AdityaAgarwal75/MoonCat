package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Addon
import org.junit.Assert.*
import org.junit.Test

class MoonCatStreamProvidersTest {
    private fun addon(id: String, url: String) = Addon(
        id = id, name = id, version = "1", description = null, logo = null,
        baseUrl = url, catalogs = emptyList(), types = emptyList(), resources = emptyList()
    )

    @Test fun `only configured Torrentio is retained`() {
        assertTrue(MoonCatStreamProviders.isAllowed(addon("com.stremio.torrentio", "https://torrentio.strem.fun/sort=seeders")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("db", "https://torrentsdb.com/config")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("other", "https://torrentsdb.com.attacker.example")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("other", "https://comet.elfhosted.com")))
    }
}
