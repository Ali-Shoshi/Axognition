package com.example.axognition.ui

import android.graphics.Rect
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.data.AssistantApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AssistantGeometryAnswerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentFigureOverridesWrongChatMeasurementsAndMissingBaseIsNotInvented() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val messages = mutableStateListOf<ChatMessage>().apply {
            repeat(12) {
                add(ChatMessage("What are the area and perimeter of this triangle?", true))
                add(ChatMessage("Its base is 3 and height is 4; the area is 6 and perimeter is 14.", false))
            }
        }
        val originalHistory = messages.map { AssistantApi.HistoryMessage(it.text, it.fromStudent) }
        val ready = CountDownLatch(1)
        lateinit var capture: AssistantScreenCapture
        var overlay: Rect? = null
        var webView: WebView? = null
        compose.setContent {
            val viewContext = LocalContext.current
            capture = androidx.compose.runtime.remember(viewContext) { AssistantScreenCapture.from(viewContext)!! }
            MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    AndroidView(factory = { viewContext ->
                        WebView(viewContext).also { view ->
                            webView = view
                            view.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                                        override fun onComplete(requestId: Long) {
                                            view.postOnAnimation { view.postOnAnimation { ready.countDown() } }
                                        }
                                    })
                                }
                            }
                            view.loadDataWithBaseURL(null, """<html><meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:0;background:white;color:black"><svg viewBox="0 0 1100 700" width="100%"><text x="40" y="50" font-size="26">Current triangle</text><path d="M250 130 L80 500 L430 500 Z" fill="none" stroke="black" stroke-width="4"/><path d="M250 130 L250 500" fill="none" stroke="black" stroke-width="2"/><text x="270" y="380" font-size="36">h = 3</text><text x="380" y="310" font-size="36">4</text></svg></body></html>""", "text/html", "UTF-8", null)
                        }
                    }, modifier = Modifier.fillMaxSize())
                    AssistantChatPanel(expanded = true, anchor = Offset(with(density) { maxWidth.toPx() * 0.67f }, 40f),
                        onMove = {}, messages = messages, onMessage = { messages.add(it) }, onDismiss = {},
                        listeningMode = VoiceListeningMode.OFF, onListeningModeChange = {}, onVoiceInput = {},
                        onAudioBusyChange = {}, voiceStatus = null,
                        captureScreen = { capture.capture() },
                        onAssistantBoundsChanged = { overlay = it; capture.setAssistantBounds(it) })
                }
            }
        }
        try {
            assertTrue(ready.await(20, TimeUnit.SECONDS))
            compose.waitForIdle()
            assertNotNull("Chat overlay bounds were not registered", overlay)
            val image = runBlocking { capture.capture() }
            assertNotNull(image)
            compose.onNode(hasSetTextAction()).performTextInput("Sa është perimetri dhe sipërfaqja e këtij trekëndëshi?")
            compose.onNodeWithContentDescription(tr("Send question")).performClick()
            compose.waitUntil(45_000) { messages.size == 26 }
            checkMissingMeasurements(messages.last().text)
            val streamed = AssistantApi.streamQuestion("Sa është perimetri dhe sipërfaqja e këtij trekëndëshi?",
                originalHistory, screenImage = image) {}
            checkMissingMeasurements(streamed)
        } finally {
            compose.runOnIdle { capture.setAssistantBounds(null); webView?.destroy() }
        }
    }

    private fun checkMissingMeasurements(answer: String) {
        assertTrue("Base must be missing, not invented: $answer", answer.contains("Mungon baza"))
        assertTrue("Height must stay 3: $answer", answer.contains("baza × 3 / 2"))
        assertTrue("Perimeter needs all three sides: $answer", answer.contains("të tria brinjët"))
        assertFalse("Old wrong values were reused: $answer", answer.contains("= 6") || answer.contains("= 14"))
    }
}
