package com.example.axognition.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.data.AssistantConversationStore
import com.example.axognition.data.AssistantApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class VoiceConversationMemoryTest {
    /** Runs real streamed replies and speech, with isolated saved data and no microphone input. */
    @Test fun voiceTurnsRememberPersonalFactsCorrectionsAndRestoredConversation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("voice-memory-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val replies = LinkedBlockingQueue<ChatMessage>()
        val sentHistory = mutableListOf<List<ChatMessage>>()
        lateinit var store: AssistantConversationStore
        lateinit var controller: VoiceController
        var controllerCreated = false
        var captureCount = 0
        var originalLanguage = "en"
        try {
            instrumentation.runOnMainSync {
                AppLanguage.initialize(context)
                originalLanguage = AppLanguage.code
                AppLanguage.select(context, "en")
                store = AssistantConversationStore(preferences)
                val conversationId = store.selectedId
                // Capture this provider once, as the remembered voice controller does.
                val readHistory = { store.messages(conversationId) }
                controller = VoiceController(context, scope,
                    history = { readHistory().also { sentHistory.add(it) } },
                    save = { message ->
                        store.append(conversationId, message)
                        if (!message.fromStudent) replies.add(message)
                    }, publish = {}, captureScreen = {
                        captureCount++
                        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        val bytes = ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            output.toByteArray()
                        }
                        bitmap.recycle()
                        AssistantApi.ScreenImage("image/png", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    })
                controllerCreated = true
                controller.configure(VoiceListeningMode.WAKE_WORD)
                controller.ask("My favorite color is purple.")
            }
            assertNotNull("First spoken reply was not saved", replies.poll(60, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { controller.ask("Actually, my favorite color is turquoise now.") }
            assertNotNull("Correction reply was not saved", replies.poll(60, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                // Reload persisted history without recreating the captured provider.
                store = AssistantConversationStore(preferences)
                controller.ask("What is my favorite color?")
            }
            val recalled = replies.poll(60, TimeUnit.SECONDS)
            assertNotNull("Recall reply was not saved", recalled)
            assertTrue("Voice forgot the latest correction: ${recalled?.text}",
                recalled!!.text.contains("turquoise", ignoreCase = true))
            instrumentation.runOnMainSync {
                assertEquals(listOf(0, 2, 4), sentHistory.map { it.size })
                assertEquals("Every voice question must capture its own screen", 3, captureCount)
                assertEquals("My favorite color is purple.", sentHistory.last()[0].text)
                assertEquals("Actually, my favorite color is turquoise now.", sentHistory.last()[2].text)
                assertEquals(6, store.selected.messages.size)
                val originalId = store.selectedId
                store.create()
                assertTrue(store.selected.messages.isEmpty())
                assertEquals(6, store.messages(originalId).size)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (controllerCreated) controller.close()
                AppLanguage.select(context, originalLanguage)
            }
            scope.cancel()
            preferences.edit().clear().commit()
        }
    }
}
