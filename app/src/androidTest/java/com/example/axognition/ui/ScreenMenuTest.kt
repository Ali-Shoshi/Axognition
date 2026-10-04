package com.example.axognition.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.ui.theme.AxognitionTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allScreensNavigateInBothLayoutsThemesAndLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val originalLanguage = AppLanguage.code
        val landscape = mutableStateOf(false)
        val dark = mutableStateOf(false)
        var destination = ""
        val entries = listOf(
            "Lectures" to "Lectures", "Homework" to "Homeworks", "Practice" to "Practice",
            "Test" to "Test", "Courses" to "Courses", "Books" to "Books",
            "Exercises" to "Exersies", "Games" to "Games", "Messages & Calls" to "Call-Messages", "Map" to "Map"
        )
        compose.setContent {
            AxognitionTheme(dark.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (landscape.value) 760.dp else 390.dp, if (landscape.value) 360.dp else 760.dp).testTag("menu-preview")) {
                        DashboardScreen("Alex", onItemClick = { destination = it })
                    }
                }
            }
        }
        try {
            for (language in listOf("en", "sq")) {
                for (wide in listOf(false, true)) {
                    for (night in listOf(false, true)) {
                        compose.runOnIdle {
                            AppLanguage.select(context, language)
                            landscape.value = wide
                            dark.value = night
                        }
                        compose.onNodeWithTag("screen-menu-grid").performScrollToIndex(0)
                        if (wide) compose.onNodeWithText(tr("Map")).assertIsDisplayed()
                        if (language == "en") {
                            val output = File(context.getExternalFilesDir(null), "menu-previews").apply { mkdirs() }
                            val name = "${if (wide) "landscape" else "portrait"}-${if (night) "dark" else "light"}.png"
                            File(output, name).outputStream().use {
                                compose.onNodeWithTag("menu-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                        entries.forEachIndexed { index, (title, route) ->
                            compose.onNodeWithTag("screen-menu-grid").performScrollToIndex(index)
                            compose.onNodeWithText(tr(title)).assertIsDisplayed().performClick()
                            compose.runOnIdle { assertEquals(route, destination) }
                        }
                    }
                }
            }
        } finally {
            compose.runOnIdle { AppLanguage.select(context, originalLanguage) }
        }
    }

    @Test fun drawerRemainsScrollableInAShortWindow() {
        val dark = mutableStateOf(false)
        var destination = ""
        compose.setContent {
            AxognitionTheme(dark.value) {
                Box(Modifier.size(320.dp, 320.dp)) {
                    AppNavigationMenu("Alex", "panel_settings", dark.value, { dark.value = it }, { destination = it }, {})
                }
            }
        }
        compose.onNodeWithTag("app-menu-drawer").performScrollToNode(hasText(tr("Settings")))
        compose.onNodeWithText(tr("Settings")).assertIsSelected().performClick()
        compose.runOnIdle { assertEquals("panel_settings", destination) }
        compose.onNodeWithTag("app-menu-drawer").performScrollToNode(hasText(tr("Dark Mode")))
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle { assertTrue(dark.value) }
    }
}
