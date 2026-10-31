package com.nuvio.tv.core.mooncat

import com.nuvio.tv.domain.model.Addon
import org.junit.Assert.*
import org.junit.Test

class MoonCatStreamProvidersTest {
    private fun addon(id: String, url: String) = Addon(
        id = id, name = id, version = "1", description = null, logo = null,
        baseUrl = url, catalogs = emptyList(), types = emptyList(), resources = emptyList()
    )

    @Test fun `configured addons are accepted while TorrentsDB stays excluded`() {
        assertTrue(MoonCatStreamProviders.isAllowed(addon("com.stremio.torrentio", "https://torrentio.strem.fun/sort=seeders")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("db", "https://torrentsdb.com/config")))
        assertTrue(MoonCatStreamProviders.isAllowed(addon("other", "https://torrentsdb.com.attacker.example")))
        assertTrue(MoonCatStreamProviders.isAllowed(addon("other", "https://comet.elfhosted.com")))
        assertTrue(MoonCatStreamProviders.isAllowed(addon("custom", "https://my-addon.example/config")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("custom", "http://remote.example")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("custom", "not a url")))
        assertFalse(MoonCatStreamProviders.isAllowed(addon("custom", "https://example.com").copy(enabled = false)))
    }
}
