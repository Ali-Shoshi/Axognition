package com.example.axognition.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.axognition.data.AssistantApi
import kotlinx.coroutines.*

data class VoiceAssistantBubble(
    val text: String,
    val isAnswer: Boolean = false,
    val readThrough: Int = 0,
    val currentStart: Int = -1,
    val currentEnd: Int = -1,
    val isSpeaking: Boolean = false
)

@Composable
fun WakeWordAssistant(
    listeningMode: VoiceListeningMode,
    suspended: Boolean = false,
    conversation: () -> List<ChatMessage>,
    onMessage: (ChatMessage) -> Unit,
    content: @Composable (VoiceAssistantBubble?, () -> Unit, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val latestHistory by rememberUpdatedState(conversation)
    val latestMessage by rememberUpdatedState(onMessage)
    var bubble by remember { mutableStateOf<VoiceAssistantBubble?>(null) }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var permissionPrompted by remember(listeningMode) { mutableStateOf(false) }
    val controller = remember(context, AppLanguage.code) {
        VoiceController(context, scope, { latestHistory() }, { latestMessage(it) }, { bubble = it })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRevision++
        if (!it) bubble = VoiceAssistantBubble("Microphone permission is needed for “Hej AI”.")
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) controller.configure(VoiceListeningMode.OFF)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.close()
        }
    }
    LaunchedEffect(listeningMode, suspended, foreground, controller, permissionRevision) {
        controller.configure(VoiceListeningMode.OFF)
        if (listeningMode != VoiceListeningMode.OFF && foreground && !suspended) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                controller.configure(listeningMode)
            } else if (!permissionPrompted) {
                permissionPrompted = true
                permission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                bubble = VoiceAssistantBubble("Microphone permission is needed for “Hej AI”.")
            }
        }
    }
    content(bubble, { controller.interrupt() }, { controller.listenNow() })
}

/**
 * One recognizer session and one answer at a time. Recognition is paused during
 * playback so the speaker cannot transcribe itself as the student's next turn.
 * All state and audio callbacks are serialized on the main thread.
 */
internal class VoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val history: () -> List<ChatMessage>,
    private val save: (ChatMessage) -> Unit,
    private val publish: (VoiceAssistantBubble?) -> Unit,
    private val prepareRecognitionIntent: (Intent) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private val session = VoiceConversation()
    private var selection = VoiceListeningMode.OFF
    private var bubble: VoiceAssistantBubble? = null
        set(value) { field = value; publish(value) }
    private var recognizer: SpeechRecognizer? = null
    private var listenJob: Job? = null
    private var recognitionDeadline: Job? = null
    private var recognitionSettle: Job? = null
    private var recognitionFinish: Job? = null
    private var answerJob: Job? = null
    private var request: AssistantApi.RequestCancellation? = null
    private var generation = 0
    private var recognitionGeneration = 0
    private var expectingQuestion = false
    private var retryDelay = 450L
    private var disposed = false
    private var ttsReady = false
    private var answerText = ""
    private var spokenThrough = 0
    private var savedAnswer = false
    private var responseComplete = false
    private var speechFailed = false
    private var playbackWatchdog: Job? = null
    private val utterances = mutableMapOf<String, Pair<Int, Int>>()
    private val speaker: TextToSpeech = createAppTextToSpeech(context) { status ->
        main.post { if (!disposed) ttsReady = status == TextToSpeech.SUCCESS }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { main.post {
                if (utterances.containsKey(id)) bubble = bubble?.copy(isSpeaking = true)
            } }
            override fun onRangeStart(id: String, start: Int, end: Int, frame: Int) { main.post {
                val offset = utterances[id]?.first ?: return@post
                val first = (offset + start).coerceIn(0, answerText.length)
                val last = (offset + end).coerceIn(first, answerText.length)
                spokenThrough = maxOf(spokenThrough, first)
                bubble = bubble?.copy(readThrough = last, currentStart = first, currentEnd = last, isSpeaking = true)
            } }
            override fun onDone(id: String) { main.post {
                val range = utterances.remove(id)
                if (range != null) {
                    spokenThrough = maxOf(spokenThrough, range.second)
                    bubble = bubble?.copy(readThrough = spokenThrough, currentStart = -1, currentEnd = -1)
                }
                if (id == "finish-$generation") finishAnswer()
            } }
            @Deprecated("Android callback")
            override fun onError(id: String) { main.post {
                if (utterances.remove(id) != null || id == "finish-$generation") {
                    speaker.stop()
                    utterances.clear()
                    speechFailed = true
                    bubble = bubble?.copy(isSpeaking = false)
                    if (responseComplete) finishAnswer()
                }
            } }
        })
    }

    fun configure(mode: VoiceListeningMode) {
        cancelTurn()
        stopListening()
        session.reset()
        selection = mode
        expectingQuestion = false
        bubble = null
        if (mode != VoiceListeningMode.OFF) scheduleListening(0)
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        recognitionGeneration++
        clearRecognitionTimers()
        recognizer?.cancel()
    }

    private fun clearRecognitionTimers() {
        recognitionDeadline?.cancel()
        recognitionSettle?.cancel()
        recognitionFinish?.cancel()
        recognitionDeadline = null
        recognitionSettle = null
        recognitionFinish = null
    }

    private fun cancelTurn() {
        generation++
        request?.cancel()
        request = null
        answerJob?.cancel()
        answerJob = null
        playbackWatchdog?.cancel()
        playbackWatchdog = null
        speaker.stop()
        utterances.clear()
        if (!savedAnswer && spokenThrough > 0) {
            save(ChatMessage(answerText.take(spokenThrough).trimEnd(), false))
        }
        answerText = ""
        spokenThrough = 0
        savedAnswer = false
        responseComplete = false
        speechFailed = false
    }

    fun interrupt() {
        if (selection == VoiceListeningMode.OFF) return
        cancelTurn()
        stopListening()
        expectingQuestion = session.active
        bubble = if (session.active) VoiceAssistantBubble("Listening…") else null
        scheduleListening()
    }

    fun listenNow() {
        if (selection == VoiceListeningMode.OFF) return
        cancelTurn()
        stopListening()
        if (selection == VoiceListeningMode.CONVERSATION) session.start()
        expectingQuestion = true
        bubble = VoiceAssistantBubble("Listening…")
        scheduleListening(150)
    }

    private fun endConversation() {
        cancelTurn()
        stopListening()
        session.reset()
        expectingQuestion = false
        bubble = VoiceAssistantBubble("Conversation ended. Say “hej AI” to start again.")
        scheduleListening()
    }

    private fun scheduleListening(wait: Long = 600) {
        listenJob?.cancel()
        if (disposed || selection == VoiceListeningMode.OFF) return
        listenJob = scope.launch {
            delay(wait)
            if (disposed || selection == VoiceListeningMode.OFF) return@launch
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                bubble = VoiceAssistantBubble("Microphone permission is needed for “Hej AI”.")
                return@launch
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                bubble = VoiceAssistantBubble("Voice recognition is not available on this tablet.")
                return@launch
            }
            clearRecognitionTimers()
            val token = ++recognitionGeneration
            // Keep the healthy service connection. Destroy/rebind on every timeout
            // races Android's disconnect callback against the next startListening.
            val current = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            val capture = RecognitionCapture(waitingForWake = !expectingQuestion && !session.active)
            var stopping = false
            fun valid() = !disposed && selection != VoiceListeningMode.OFF && token == recognitionGeneration
            fun complete(candidates: List<String> = emptyList()) {
                if (!valid()) return
                val result = capture.finish(candidates) ?: return
                recognitionGeneration++
                clearRecognitionTimers()
                current.cancel()
                retryDelay = 600
                android.util.Log.i("AXO_VOICE",
                    "Recognition complete chars=${result.text.length} wake=${result.wakeDetected} expecting=$expectingQuestion")
                when (val turn = session.accept(result.text, selection, expectingQuestion, result.wakeDetected)) {
                    VoiceConversation.Turn.Wait -> {
                        bubble = if (session.active || expectingQuestion) VoiceAssistantBubble("Listening…") else null
                        scheduleListening()
                    }
                    VoiceConversation.Turn.Listen -> {
                        expectingQuestion = true
                        bubble = VoiceAssistantBubble("Hej AI heard — ask your question.")
                        scheduleListening()
                    }
                    VoiceConversation.Turn.End -> endConversation()
                    is VoiceConversation.Turn.Ask -> {
                        expectingQuestion = false
                        ask(turn.question)
                    }
                }
            }
            fun requestFinal() {
                if (!valid() || stopping) return
                stopping = true
                recognitionSettle?.cancel()
                runCatching { current.stopListening() }
                // Some installed engines send a blank final bundle, or no final
                // callback at all. Use the captured words once this grace expires.
                recognitionFinish = scope.launch {
                    delay(1_500)
                    if (valid()) complete()
                }
            }
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!valid()) return
                    android.util.Log.i("AXO_VOICE", "Recognition ready: ${AppLanguage.code}")
                    if (bubble?.isAnswer != true) {
                        bubble = if (session.active || expectingQuestion)
                            VoiceAssistantBubble("Hej AI heard — ask your question.") else null
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (!valid() || stopping) return
                    recognitionSettle?.cancel()
                    if (session.active || expectingQuestion) bubble = VoiceAssistantBubble("Listening…")
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (!valid() || stopping) return
                    recognitionSettle?.cancel()
                    recognitionSettle = scope.launch {
                        delay(700)
                        requestFinal()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(results: Bundle?) {
                    if (!valid()) return
                    val changed = capture.partial(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                    if (capture.wakeDetected || session.active || expectingQuestion) {
                        bubble = VoiceAssistantBubble("Listening…")
                    }
                    if (changed && !stopping) {
                        recognitionSettle?.cancel()
                        recognitionSettle = scope.launch {
                            delay(1_800)
                            requestFinal()
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    complete(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                }
                override fun onSegmentResults(results: Bundle) {
                    complete(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                }
                override fun onEndOfSegmentedSession() { complete() }
                override fun onError(error: Int) {
                    if (!valid()) return
                    android.util.Log.w("AXO_VOICE", "Recognition error=$error language=${AppLanguage.code}")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        complete()
                        return
                    }
                    recognitionGeneration++
                    clearRecognitionTimers()
                    current.cancel()
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            bubble = VoiceAssistantBubble("Microphone permission is needed for “Hej AI”.")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            bubble = VoiceAssistantBubble("Speech recognition is unavailable for this language. Check the installed speech languages.")
                        else -> {
                            if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                                current.destroy()
                                recognizer = null
                            }
                            retryDelay = (retryDelay * 2).coerceIn(1_200, 8_000)
                            bubble = VoiceAssistantBubble(when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Microphone unavailable. Retrying…"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition connection failed. Retrying…"
                                else -> "Speech recognition is restarting…"
                            })
                            scheduleListening(retryDelay)
                        }
                    }
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Let the installed engine use its supported endpoint settings;
                // our bounded finish/grace timers handle engines that never finish.
            }
            runCatching {
                prepareRecognitionIntent(intent)
                current.startListening(intent)
                recognitionDeadline = scope.launch {
                    delay(25_000)
                    requestFinal()
                }
            }.onFailure {
                android.util.Log.w("AXO_VOICE", "Could not start recognition", it)
                recognitionGeneration++
                clearRecognitionTimers()
                bubble = VoiceAssistantBubble("Speech recognition is restarting…")
                scheduleListening(2_000)
            }
        }
    }

    private fun ask(question: String) {
        android.util.Log.i("AXO_VOICE", "Sending question chars=${question.length}")
        stopListening()
        cancelTurn()
        val token = generation
        val previous = history().map { AssistantApi.HistoryMessage(it.text, it.fromStudent) }
        save(ChatMessage(question, true))
        bubble = VoiceAssistantBubble("Thinking…")
        val cancellation = AssistantApi.RequestCancellation()
        request = cancellation
        var count = 0
        val chunker = StreamingSpeechChunker { chunk, offset ->
            if (token == generation && ttsReady && !speechFailed) {
                val id = "answer-$token-${count++}"
                utterances[id] = offset to offset + chunk.length
                val result = speaker.speakInAppLanguage(
                    chunk, id, queueMode = TextToSpeech.QUEUE_ADD, preferNetwork = false, naturalize = false
                )
                if (result != TextToSpeech.SUCCESS) utterances.remove(id)
            }
        }
        answerJob = scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    AssistantApi.streamQuestion(question, previous, cancellation) { partial ->
                        cancellation.check()
                        main.post {
                            if (token != generation || disposed) return@post
                            answerText = partial
                            bubble = (bubble ?: VoiceAssistantBubble(partial)).copy(text = partial, isAnswer = true)
                            if (ttsReady) chunker.accept(partial)
                        }
                    }
                }
                if (token != generation) return@launch
                answerText = answer
                responseComplete = true
                bubble = (bubble ?: VoiceAssistantBubble(answer)).copy(text = answer, isAnswer = true)
                withTimeoutOrNull(5_000) { while (!ttsReady) delay(50) }
                if (token != generation) return@launch
                chunker.accept(answer, final = true)
                android.util.Log.i("AXO_VOICE", "Answer received chars=${answer.length} speechQueued=${utterances.size}")
                if (utterances.isEmpty() || !ttsReady) {
                    finishAnswer()
                } else {
                    val queued = speaker.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, "finish-$token")
                    if (queued != TextToSpeech.SUCCESS) {
                        speaker.stop()
                        utterances.clear()
                        finishAnswer()
                    } else {
                        playbackWatchdog = scope.launch {
                            delay((answer.length * 160L + 15_000L).coerceAtLeast(30_000L))
                            if (token == generation && !savedAnswer) {
                                speaker.stop()
                                utterances.clear()
                                finishAnswer()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation) return@launch
                speaker.stop()
                utterances.clear()
                if (answerText.isNotBlank()) {
                    finishAnswer()
                } else {
                    bubble = VoiceAssistantBubble("I could not reach the learning assistant. Please try again.")
                    expectingQuestion = session.active
                    scheduleListening(1_000)
                }
            }
        }
    }

    private fun finishAnswer() {
        if (disposed || selection == VoiceListeningMode.OFF) return
        playbackWatchdog?.cancel()
        if (!savedAnswer && answerText.isNotBlank()) {
            save(ChatMessage(answerText, false))
            savedAnswer = true
        }
        bubble = bubble?.copy(isSpeaking = false, currentStart = -1, currentEnd = -1)
        expectingQuestion = session.active
        // Brief drain interval prevents the microphone picking up the end of playback.
        scheduleListening(500)
    }

    fun close() {
        disposed = true
        configure(VoiceListeningMode.OFF)
        recognizer?.destroy()
        recognizer = null
        speaker.shutdown()
    }
}

/** Emits complete sentences, or a readable phrase when the first sentence is long. */
internal class StreamingSpeechChunker(
    private val onChunk: (text: String, responseOffset: Int) -> Unit
) {
    private var emittedThrough = 0
    private val sentenceEnd = Regex("""[.!?](?:["'”’)]*)(?:\s|$)""")

    fun accept(fullText: String, final: Boolean = false) {
        while (emittedThrough < fullText.length) {
            val remaining = fullText.substring(emittedThrough)
            val sentenceBoundary = sentenceEnd.findAll(remaining)
                .firstOrNull { it.range.last + 1 >= 24 }?.let { it.range.last + 1 }
            val rawLength = when {
                sentenceBoundary != null -> sentenceBoundary
                remaining.length >= 96 -> remaining.lastIndexOf(' ', startIndex = 84)
                    .takeIf { it >= 48 }?.plus(1) ?: return
                final -> remaining.length
                else -> return
            }
            val rawEnd = emittedThrough + rawLength
            val raw = fullText.substring(emittedThrough, rawEnd)
            val first = raw.indexOfFirst { !it.isWhitespace() }
            if (first >= 0) {
                val lastExclusive = raw.indexOfLast { !it.isWhitespace() } + 1
                onChunk(raw.substring(first, lastExclusive), emittedThrough + first)
            }
            emittedThrough = rawEnd
        }
    }
}
