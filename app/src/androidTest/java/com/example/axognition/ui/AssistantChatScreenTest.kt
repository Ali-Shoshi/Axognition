package com.example.axognition.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.example.axognition.data.AssistantConversationStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class AssistantChatScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickerPreservesExistingHistoryDraftsAndRoutesLateRepliesToOriginalConversation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val originalLanguage = AppLanguage.code
        val preferences = context.getSharedPreferences("conversation-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val otherPreferences = context.getSharedPreferences("conversation-other-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        preferences.edit().putString("messages", JSONArray().put(
            JSONObject().put("text", "Explain fractions").put("fromStudent", true)
        ).toString()).commit()
        val store = AssistantConversationStore(preferences)
        val originalId = store.selectedId
        compose.setContent {
            MaterialTheme {
                val active = store.selected
                AssistantChatPanel(
                    expanded = true, anchor = Offset.Zero, onMove = {},
                    messages = active.messages,
                    onMessage = { store.append(active.id, it) },
                    onDismiss = {}, listeningMode = VoiceListeningMode.OFF,
                    onListeningModeChange = {}, onVoiceInput = {}, onAudioBusyChange = {},
                    voiceStatus = null, fullScreen = true,
                    conversationId = active.id, conversations = store.conversations,
                    onSelectConversation = store::select, onNewConversation = store::create
                )
            }
        }
        try {
            compose.onNode(hasSetTextAction()).performTextInput("Draft about fractions")
            compose.onNodeWithContentDescription(tr("Choose conversation")).performClick()
            compose.onNodeWithText(tr("New conversation")).performClick()
            val newId = store.selectedId
            assertNotEquals(originalId, newId)
            compose.onNodeWithText(tr("What would you like to learn?")).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).assert(hasText(""))
            compose.onNode(hasSetTextAction()).performTextInput("Draft about shapes")
            compose.runOnIdle {
                // Simulate a pending reply arriving after the user has selected another chat.
                store.append(originalId, ChatMessage("A fraction is part of a whole.", false))
                store.append(newId, ChatMessage("Tell me about shapes", true))
            }
            compose.onNodeWithText("A fraction is part of a whole.").assertDoesNotExist()
            for (language in listOf("en", "sq")) {
                compose.runOnIdle { AppLanguage.select(context, language) }
                compose.onNodeWithContentDescription(tr("Choose conversation")).performClick()
                compose.onNode(hasText("Explain fractions") and hasClickAction()).performClick()
                compose.onNode(hasSetTextAction()).assertTextContains("Draft about fractions")
                compose.onNodeWithText("A fraction is part of a whole.").assertIsDisplayed()
                compose.onNodeWithContentDescription(tr("Choose conversation")).performClick()
                compose.onNode(hasText("Tell me about shapes") and hasClickAction()).performClick()
                compose.onNode(hasSetTextAction()).assertTextContains("Draft about shapes")
                compose.onNodeWithText("A fraction is part of a whole.").assertDoesNotExist()
            }
            val restored = AssistantConversationStore(preferences)
            assertEquals(newId, restored.selectedId)
            assertEquals(2, restored.conversations.size)
            assertEquals("A fraction is part of a whole.", restored.conversations.first { it.id == originalId }.messages.last().text)
            assertEquals("Tell me about shapes", restored.selected.messages.single().text)
            assertTrue(AssistantConversationStore(otherPreferences).selected.messages.isEmpty())
        } finally {
            compose.runOnIdle { AppLanguage.select(context, originalLanguage) }
            preferences.edit().clear().commit()
            otherPreferences.edit().clear().commit()
        }
    }

    @Test fun robotOpensEmptyChatAndDraftAndIncomingMessagesSurviveReturning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val originalLanguage = AppLanguage.code
        val fullScreen = mutableStateOf(false)
        val messages = mutableStateListOf<ChatMessage>()
        compose.setContent {
            MaterialTheme {
                AssistantChatPanel(
                    expanded = true,
                    anchor = Offset(16f, 16f),
                    onMove = {},
                    messages = messages,
                    onMessage = { messages.add(it) },
                    onDismiss = {},
                    listeningMode = VoiceListeningMode.OFF,
                    onListeningModeChange = {},
                    onVoiceInput = {},
                    onAudioBusyChange = {},
                    voiceStatus = null,
                    fullScreen = fullScreen.value,
                    onOpenFullScreen = { fullScreen.value = true },
                    onExitFullScreen = { fullScreen.value = false }
                )
            }
        }
        try {
            for (language in listOf("en", "sq")) {
                compose.runOnIdle { AppLanguage.select(context, language) }
                compose.onNode(hasSetTextAction()).performTextClearance()
                compose.onNode(hasSetTextAction()).performTextInput("Explain fractions")
                compose.onNodeWithContentDescription(tr("Open full-screen AI chat")).assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription(tr("Back to floating chat")).assertIsDisplayed()
                compose.onNode(hasSetTextAction()).assertTextContains("Explain fractions")
                if (messages.isEmpty()) {
                    compose.onNodeWithText(tr("What would you like to learn?")).assertIsDisplayed()
                    compose.runOnIdle { messages.add(ChatMessage("A fraction is part of a whole.", false)) }
                }
                compose.onNodeWithText("A fraction is part of a whole.").assertIsDisplayed()
                compose.onNodeWithContentDescription(tr("Back to floating chat")).performClick()
                compose.onNodeWithContentDescription(tr("Open full-screen AI chat")).assertIsDisplayed()
                compose.onNode(hasSetTextAction()).assertTextContains("Explain fractions")
                compose.onNodeWithText("A fraction is part of a whole.").assertIsDisplayed()
                compose.onNodeWithContentDescription(tr("Open full-screen AI chat")).performClick()
                Espresso.closeSoftKeyboard()
                Espresso.pressBack()
                compose.onNodeWithContentDescription(tr("Open full-screen AI chat")).assertIsDisplayed()
            }
        } finally {
            compose.runOnIdle { AppLanguage.select(context, originalLanguage) }
        }
    }
}
