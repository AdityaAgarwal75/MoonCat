package com.nuvio.tv.core.catalog

import org.junit.Assert.*
import org.junit.Test

class MoonCatHomeCatalogPolicyTest {
    @Test fun `regional shelves are hidden from both old manifest ids and display names`() {
        assertTrue(MoonCatHomeCatalogPolicy.hidden("tamil", "Movies"))
        assertTrue(MoonCatHomeCatalogPolicy.hidden("te", "Movies"))
        assertTrue(MoonCatHomeCatalogPolicy.hidden("some-addon", "Telugu movies"))
        assertTrue(MoonCatHomeCatalogPolicy.hidden("regional", "Regional favourites"))
        assertFalse(MoonCatHomeCatalogPolicy.hidden("language", "Hindi movies"))
        assertFalse(MoonCatHomeCatalogPolicy.hidden("language", "English series"))
        assertFalse(MoonCatHomeCatalogPolicy.hidden("popular", "Popular movies"))
    }
}
