package com.example.axognition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class VoiceModeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeModesCanBeTappedAndDraggedInBothLanguagesAndThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val original = AppLanguage.code
        val selection = mutableStateOf(VoiceListeningMode.OFF)
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(280.dp).testTag("selector")) {
                    VoiceModeSelector(selection.value) { selection.value = it }
                }
            }
        }
        try {
            for (language in listOf("en", "sq")) {
                for (night in listOf(false, true)) {
                    compose.runOnIdle {
                        AppLanguage.select(context, language)
                        dark.value = night
                    }
                    for (mode in VoiceListeningMode.entries) {
                        compose.onNodeWithText(tr(mode.label)).assertIsDisplayed().performClick().assertIsSelected()
                    }
                    compose.onNodeWithTag("selector").performTouchInput { swipeLeft() }
                    compose.onNodeWithText(tr("Off")).assertIsSelected()
                    compose.onNodeWithTag("selector").performTouchInput { swipeRight() }
                    compose.onNodeWithText(tr("Conversation")).assertIsSelected()
                }
            }
        } finally {
            compose.runOnIdle { AppLanguage.select(context, original) }
        }
    }
}
