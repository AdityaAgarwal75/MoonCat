package com.nuvio.tv.ui.screens.discovery

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.ui.screens.collection.MooncatStudiosScreen
import com.nuvio.tv.ui.screens.news.MooncatNewsScreen
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MooncatDiscoveryUiTest {
    @get:Rule val ui = createComposeRule()
    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun studiosLoadsPosterShelvesAndOpensMarvelCatalogue() {
        ui.setContent { NuvioTheme { MooncatStudiosScreen {} } }
        try {
            ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("Marvel").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            screenshot("studios-failed")
            println(ui.onRoot().printToString())
            throw failure
        }
        screenshot("studios")
        ui.onNodeWithContentDescription("Marvel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        ui.waitUntil(5000) { ui.onAllNodesWithText("● All titles").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("● All titles").assertExists()
        ui.onNodeWithText("Movies").assertExists()
        ui.onNodeWithText("Series").assertExists()
        ui.onAllNodesWithText("Iron Man").onFirst().assertExists()
        screenshot("marvel")
    }
    @Test fun newsShowsLogosAndFiltersEnglishChannels() {
        ui.setContent { NuvioTheme { MooncatNewsScreen {} } }
        ui.onNodeWithContentDescription("DD News").assertExists()
        screenshot("news")
        ui.onAllNodesWithText("English").onFirst().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("DD News").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription("India Today").assertExists()
        ui.onNodeWithContentDescription("DD News").assertDoesNotExist()
        screenshot("news-english")
    }
}
