package com.nuvio.tv.updater

import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import org.junit.Assert.*
import org.junit.Test

class MooncatApkSelectionTest {
    @Test fun `TV update never selects the mobile asset from a mixed release`() {
        val phone = GitHubAssetDto("MoonCat-v1.0.4-Mobile.apk", "https://example.com/mobile.apk")
        val tv = GitHubAssetDto("MoonCat-v1.0.4-FireTV-armeabi-v7a.apk", "https://example.com/tv.apk")
        assertEquals(tv, AbiSelector.chooseBestApkAsset(listOf(phone, tv)))
        assertNull(AbiSelector.chooseBestApkAsset(listOf(phone)))
    }
}
