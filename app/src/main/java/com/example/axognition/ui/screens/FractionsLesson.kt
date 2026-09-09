package com.example.axognition.ui.screens

import android.annotation.SuppressLint
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.axognition.BuildConfig
import java.util.Locale

/** Hosts the server-authored lesson; Android speech also works without browser speech support. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun FractionsLesson(onBack: () -> Unit) {
    val context = LocalContext.current
    val url = BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/') + "/lessons/fractions.html"
    var ready by remember { mutableStateOf(false) }
    val speech = remember { TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS } }
    var web by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(ready) {
        if (ready) { speech.language = Locale.US; speech.setSpeechRate(0.9f) }
    }
    DisposableEffect(Unit) {
        onDispose {
            speech.stop()
            speech.shutdown()
            web?.removeJavascriptInterface("LessonVoice")
            web?.destroy()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = onBack) { Text("Back to lectures") }
            TextButton(onClick = { web?.reload() }) { Text("Reload lesson") }
        }
        if (!ready) Text("Preparing narration. Captions are always available.")
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                            request.url.toString() != url
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun speak(text: String) {
                            speech.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "lesson")
                        }
                        @JavascriptInterface fun stop() { speech.stop() }
                    }, "LessonVoice")
                    web = this
                    loadUrl(url)
                }
            }
        )
    }
}
