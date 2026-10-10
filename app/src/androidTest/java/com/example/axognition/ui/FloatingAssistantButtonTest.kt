package com.example.axognition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FloatingAssistantButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun questionsAndAnswersFitEveryCornerAndRestoreTheSavedRobotPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val language = AppLanguage.code
        val state = mutableStateOf<VoiceAssistantBubble?>(null)
        val anchor = mutableStateOf(Offset.Zero)
        val dark = mutableStateOf(false)
        val wide = mutableStateOf(false)
        var density = 1f
        var moves = 0
        val question = "Why do triangles have three sides?"
        compose.setContent {
            density = LocalDensity.current.density
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.size(if (wide.value) 600.dp else 340.dp, 320.dp).testTag("host")) {
                    FloatingAssistantButton(anchor.value, state.value, { anchor.value = it; moves++ }, {}, {}, {})
                }
            }
        }
        try {
            for (code in listOf("en", "sq")) for (night in listOf(false, true)) for (landscape in listOf(false, true)) {
                compose.runOnIdle { AppLanguage.select(context, code); dark.value = night; wide.value = landscape }
                val width = if (landscape) 600f else 340f
                for (corner in listOf(Offset(16f, 40f), Offset(width - 72f, 40f), Offset(16f, 248f), Offset(width - 72f, 248f))) {
                    val saved = corner * density
                    compose.runOnIdle { state.value = null; anchor.value = saved }
                    val original = compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot
                    compose.runOnIdle { state.value = VoiceAssistantBubble(question, isQuestion = true) }
                    assertPillFits()
                    compose.onNodeWithText(question).assertIsDisplayed()
                    assertTrue(compose.onNodeWithTag("assistantQuestion").fetchSemanticsNode().boundsInRoot.right <=
                        compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot.left + 1f)
                    compose.runOnIdle {
                        state.value = VoiceAssistantBubble("A triangle is a shape with three straight sides.", isAnswer = true,
                            question = question, isSpeaking = true, currentStart = 2, currentEnd = 10)
                    }
                    assertPillFits()
                    compose.onNodeWithText(question).assertIsDisplayed()
                    assertTrue(compose.onNodeWithTag("assistantAnswer").fetchSemanticsNode().boundsInRoot.left >=
                        compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot.right - 1f)
                    if (code == "en" && corner.x > width / 2 && corner.y == 40f) {
                        val file = java.io.File(context.getExternalFilesDir(null), "assistant-${if (night) "dark" else "light"}-${if (landscape) "wide" else "narrow"}.png")
                        java.io.FileOutputStream(file).use {
                            compose.onNodeWithTag("host").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    compose.runOnIdle { assertEquals(saved, anchor.value); assertEquals(0, moves); state.value = state.value!!.copy(isComplete = true) }
                    compose.onNodeWithTag("assistantAnswer").assertDoesNotExist()
                    val returned = compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot
                    assertEquals(original.left, returned.left, 1f)
                    assertEquals(original.top, returned.top, 1f)
                    // Conversation mode resumes listening without moving the robot again.
                    compose.runOnIdle { state.value = VoiceAssistantBubble("Listening…") }
                    assertEquals(original.left, compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot.left, 1f)
                }
            }
        } finally { compose.runOnIdle { AppLanguage.select(context, language) } }
    }

    @Test fun draggingDuringAnAnswerBecomesTheNewReturnPositionAndDismissStillWorks() {
        val state = mutableStateOf<VoiceAssistantBubble?>(VoiceAssistantBubble("This is the answer.", isAnswer = true, question = "My question"))
        val anchor = mutableStateOf(Offset(16f, 120f))
        var saves = 0
        var opens = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(600.dp, 360.dp).testTag("host")) {
                    FloatingAssistantButton(anchor.value, state.value, { anchor.value = it }, { saves++ }, { opens++ }, { state.value = null })
                }
            }
        }
        compose.onNodeWithTag("assistantRobot").performTouchInput { swipe(center, center + Offset(80f, 80f), 350) }
        assertPillFits()
        compose.runOnIdle { assertEquals(1, saves) }
        val robot = compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(tr("Stop speaking and dismiss")).performClick()
        val returned = compose.onNodeWithTag("assistantRobot").fetchSemanticsNode().boundsInRoot
        assertEquals(robot.left, returned.left, 1f)
        assertEquals(robot.top, returned.top, 1f)
        compose.onNodeWithTag("assistantRobot").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    private fun assertPillFits() {
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithTag("assistantPill").fetchSemanticsNode().boundsInRoot
        assertTrue("Left text outside screen: $pill", pill.left >= host.left)
        assertTrue("Right text outside screen: $pill", pill.right <= host.right)
        assertTrue("Top outside screen: $pill", pill.top >= host.top)
        assertTrue("Bottom outside screen: $pill", pill.bottom <= host.bottom)
    }
}
