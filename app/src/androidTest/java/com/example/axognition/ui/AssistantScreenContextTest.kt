package com.example.axognition.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.io.File

class AssistantScreenContextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capturesRenderedWebViewAndBothQuestionPathsUseRelevantScreenWithoutDistractingUnrelatedReplies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val originalLanguage = AppLanguage.code
        AppLanguage.select(context, "en")
        val ready = CountDownLatch(1)
        lateinit var capture: AssistantScreenCapture
        var webView: WebView? = null
        compose.setContent {
            capture = AssistantScreenCapture.from(LocalContext.current)!!
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Mathematics practice")
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
                            view.loadDataWithBaseURL(null, """<html><meta name="viewport" content="width=device-width,initial-scale=1"><body style="background:white;color:black;font:36px sans-serif"><h3>Practice problem</h3><p>8 x 7 = ?</p><div style="height:120px;background:#00aa44"></div></body></html>""", "text/html", "UTF-8", null)
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        try {
            assertTrue(ready.await(20, TimeUnit.SECONDS))
            compose.waitForIdle()
            val image = runBlocking { capture.capture() }
            assertNotNull("Current app window was not captured", image)
            val bytes = Base64.decode(image!!.base64, Base64.DEFAULT)
            File(context.cacheDir, "screen-context-test.jpg").writeBytes(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(maxOf(bitmap.width, bitmap.height) <= 1600)
            var greenPixels = 0
            for (y in 0 until bitmap.height step 10) for (x in 0 until bitmap.width step 10) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.green(pixel) > 120 && Color.red(pixel) < 60 && Color.blue(pixel) < 110) greenPixels++
            }
            bitmap.recycle()
            assertTrue("Hardware WebView content was missing from the screenshot", greenPixels > 100)
            val typed = AssistantApi.sendQuestion("Help me solve the problem shown on my screen.", screenImage = image)
            assertTrue("Typed answer did not use the visible problem: $typed", typed.contains("56"))
            val spoken = AssistantApi.streamQuestion("What is the answer to this problem?", screenImage = image) {}
            assertTrue("Spoken answer did not use the visible problem: $spoken", spoken.contains("56"))
            val unrelated = AssistantApi.streamQuestion("What is the capital of France?", screenImage = image) {}
            assertTrue("Unrelated question was distracted by the screenshot: $unrelated", unrelated.contains("Paris", true))
            assertFalse(unrelated.contains("56"))
            val body = AssistantApi.requestBody("Help me solve this.", listOf(AssistantApi.HistoryMessage("Earlier question", true)), "en", image)
            assertEquals(image.base64, body.getJSONObject("screenImage").getString("base64"))
            assertEquals(1, body.getJSONArray("history").length())
            assertFalse(body.getJSONArray("history").toString().contains(image.base64))
        } finally {
            compose.runOnIdle { webView?.destroy(); AppLanguage.select(context, originalLanguage) }
        }
    }
}
