package com.example.axognition.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.axognition.BuildConfig
import com.example.axognition.data.LectureProgressStore
import com.example.axognition.data.ChildSessionStore
import com.example.axognition.data.LectureOutbox
import com.example.axognition.data.LectureSync
import com.example.axognition.ui.theme.LocalAxognitionDarkTheme
import com.example.axognition.ui.AppLanguage
import com.example.axognition.ui.configureNaturalAppVoice
import com.example.axognition.ui.createAppTextToSpeech
import com.example.axognition.ui.speakInAppLanguage
import com.example.axognition.ui.tr
import org.json.JSONObject

@Composable
internal fun GeometryLesson(onBack: () -> Unit) = GuidedMathLesson("geometry", onBack)

/** Shared host for narrated mathematics lessons, including speech and completion. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GuidedMathLesson(lessonId: String, onBack: () -> Unit) {
    // The activity handles orientation changes in place, so this WebView and the
    // lesson's JavaScript state remain alive while the screen is resized.
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    val progressStore = remember(context) { LectureProgressStore(context) }
    val portrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    val currentPortrait by rememberUpdatedState(portrait)
    val darkTheme = LocalAxognitionDarkTheme.current
    val currentDarkTheme by rememberUpdatedState(darkTheme)
    val background = MaterialTheme.colorScheme.background
    val owner = LocalLifecycleOwner.current
    val url = BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/') + "/lessons/$lessonId.html"
    val language = AppLanguage.code
    var speechStatus by remember { mutableStateOf("Preparing narration…") }
    val bridge = remember(lessonId) { GeometrySpeechBridge(progressStore, lessonId, context.applicationContext) }
    val speech = remember {
        createAppTextToSpeech(context) { result ->
            bridge.initialized = result == TextToSpeech.SUCCESS
            speechStatus = if (bridge.initialized) "" else "Voice unavailable. You can still use captions."
        }
    }
    LaunchedEffect(speechStatus, language) {
        if (bridge.initialized) {
            bridge.ready = speech.configureNaturalAppVoice()
            speechStatus = if (bridge.ready) "" else "Install a voice for the selected language for narration. Captions are available."
        }
    }
    var web by remember { mutableStateOf<WebView?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val leaveLesson: () -> Unit = {
        if (!leaving) {
            leaving = true
            val view = web
            if (view == null) currentOnBack()
            else view.evaluateJavascript("window.geometryExit && window.geometryExit();") { currentOnBack() }
        }
    }
    BackHandler(onBack = leaveLesson)
    // Reload the lesson data when the app language changes while the lesson is
    // already open. The initial factory load uses the same language, so this
    // effect is only observable on a subsequent language change.
    LaunchedEffect(language) {
        web?.let { view ->
            speech.stop()
            view.loadUrl("$url?theme=${if (darkTheme) "dark" else "light"}&lang=$language")
        }
    }
    DisposableEffect(speech, owner) {
        bridge.progressResponse = { request, payload -> web?.let { view -> view.post {
            if (!bridge.disposed) view.evaluateJavascript("window.lessonProgressLoaded?.(${JSONObject.quote(request)},${JSONObject.quote(payload)});", null)
        } } }
        bridge.speech = speech
        bridge.hideKeyboardAction = {
            web?.let { view -> view.post {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            } }
        }
        bridge.finishAction = { web?.post { if (!bridge.disposed) currentOnBack() } }
        speech.configureNaturalAppVoice()
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = report(utteranceId, true)
            @Deprecated("Called by older speech engines")
            override fun onError(utteranceId: String?) = report(utteranceId, false)
            override fun onError(utteranceId: String?, errorCode: Int) = report(utteranceId, false)
            private fun report(id: String?, ok: Boolean) {
                val view = web ?: return
                view.post { if (!bridge.disposed) view.evaluateJavascript(
                    "window.geometryVoiceEnd && window.geometryVoiceEnd(${JSONObject.quote(id ?: "")}, $ok);", null
                ) }
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                speech.stop()
                web?.evaluateJavascript("window.geometryPause && window.geometryPause();", null)
                web?.onPause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                web?.onResume()
                web?.evaluateJavascript("window.geometryResume && window.geometryResume();", null)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            bridge.disposed = true
            owner.lifecycle.removeObserver(observer)
            speech.stop()
            speech.shutdown()
            web?.removeJavascriptInterface("GeometryVoice")
            web?.destroy()
        }
    }
    Column(Modifier.fillMaxSize().background(background)) {
        Row {
            TextButton(onClick = leaveLesson) { Text(tr("Back to lectures")) }
            TextButton(onClick = {
                speech.stop()
                web?.loadUrl("$url?theme=${if (darkTheme) "dark" else "light"}&lang=$language")
            }) { Text(tr("Reload")) }
        }
        if (speechStatus.isNotEmpty()) Text(
            tr(speechStatus),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                WebView(it).apply {
                    setBackgroundColor(background.toArgb())
                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            view.evaluateJavascript(
                                "window.geometrySetTheme?.($currentDarkTheme);" +
                                    "window.geometrySetPortrait?.($currentPortrait);", null
                            )
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.toString().substringBefore('#').substringBefore('?') != url
                    }
                    addJavascriptInterface(bridge, "GeometryVoice")
                    web = this
                    loadUrl("$url?theme=${if (darkTheme) "dark" else "light"}&lang=$language")
                }
            },
            update = { view ->
                view.setBackgroundColor(background.toArgb())
                view.evaluateJavascript(
                    "window.geometrySetTheme?.($darkTheme);window.geometrySetPortrait?.($portrait);", null
                )
            }
        )
    }
}

private class GeometrySpeechBridge(private val progress: LectureProgressStore, private val lessonId: String, private val context: Context) {
    private val childId = ChildSessionStore.load(context)?.childId
    var progressResponse: ((String, String) -> Unit)? = null
    @Volatile var initialized = false
    @Volatile var ready = false
    @Volatile var disposed = false
    var speech: TextToSpeech? = null
    var hideKeyboardAction: (() -> Unit)? = null
    var finishAction: (() -> Unit)? = null
    @Volatile private var rate = 1f

    @JavascriptInterface
    fun progressKey(): String = progress.playerKey(lessonId)

    @JavascriptInterface
    fun trackEvent(payload: String): Boolean = runCatching {
        if (disposed || childId == null || ChildSessionStore.load(context)?.childId != childId) return false
        val saved = LectureOutbox.get(context).enqueue(childId, LectureSync.origin, lessonId, payload)
        if (saved) LectureSync.schedule(context)
        saved
    }.getOrDefault(false)

    @JavascriptInterface
    fun loadProgress(requestId: String) {
        val child = childId ?: return
        if (disposed) return
        LectureSync.executor.execute {
            val response = JSONObject()
            runCatching { LectureSync.load(context, lessonId, child) }
                .onSuccess { response.put("state", it) }
                .onFailure { response.put("error", it.message ?: "Lecture sync unavailable.") }
            response.put("pending", LectureOutbox.get(context).pending(child, LectureSync.origin, lessonId))
            if (!disposed) progressResponse?.invoke(requestId, response.toString())
        }
    }

    @JavascriptInterface
    fun setRate(value: Float) {
        if (value in setOf(0.5f, 0.7f, 1f, 1.25f, 1.5f, 1.75f, 2f)) rate = value
    }

    @JavascriptInterface
    fun hideKeyboard() { if (!disposed) hideKeyboardAction?.invoke() }

    @JavascriptInterface
    fun setCompleted(completed: Boolean): Boolean = !disposed && progress.setCompleted(lessonId, completed)

    @JavascriptInterface
    fun finish(): Boolean {
        if (disposed || lessonId !in progress.completedLectures()) return false
        speech?.stop()
        hideKeyboard()
        finishAction?.invoke()
        return true
    }

    @JavascriptInterface
    fun speak(text: String, id: String): Boolean {
        if (!ready || disposed) return false
        return speech?.speakInAppLanguage(text.take(3500), id, 0.96f * rate) == TextToSpeech.SUCCESS
    }

    @JavascriptInterface
    fun stop() { speech?.stop() }
}
