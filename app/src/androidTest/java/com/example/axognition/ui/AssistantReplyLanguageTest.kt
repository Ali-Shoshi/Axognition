package com.example.axognition.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.data.AssistantApi
import com.example.axognition.shared.AssistantLanguage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AssistantReplyLanguageTest {
    @Test fun typedAndSpokenAlbanianQuestionsUseAlbanianWithEnglishInterface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val replies = LinkedBlockingQueue<ChatMessage>()
        var controller: VoiceController? = null
        var originalLanguage = "en"
        val history = listOf(ChatMessage("Hello", true), ChatMessage("Hello! How can I help?", false))
        try {
            instrumentation.runOnMainSync {
                AppLanguage.initialize(context)
                originalLanguage = AppLanguage.code
                AppLanguage.select(context, "en")
            }
            val typed = AssistantApi.sendQuestion("Çfarë është një thyesë?", history.map { AssistantApi.HistoryMessage(it.text, it.fromStudent) })
            assertEquals("Typed answer was not Albanian: $typed", "sq", AssistantLanguage.detected(typed))
            instrumentation.runOnMainSync {
                controller = VoiceController(context, scope, history = { history },
                    save = { if (!it.fromStudent) replies.add(it) }, publish = {})
                controller!!.configure(VoiceListeningMode.WAKE_WORD)
                controller!!.ask("Cfare eshte nje thyese?")
            }
            // Saved only once real playback has finished; exercises streaming plus the Albanian TTS voice.
            val spoken = replies.poll(60, TimeUnit.SECONDS)
            assertNotNull("Spoken answer did not complete", spoken)
            assertEquals("Spoken answer was not Albanian: ${spoken?.text}", "sq", AssistantLanguage.detected(spoken!!.text))
        } finally {
            instrumentation.runOnMainSync {
                controller?.close()
                AppLanguage.select(context, originalLanguage)
            }
            scope.cancel()
        }
    }
}
