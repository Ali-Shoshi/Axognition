package com.example.axognition.ui

import com.example.axognition.ui.tr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.axognition.data.AssistantApi
import com.example.axognition.ui.createAppTextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private enum class VoiceAssistantMode { IDLE, WAITING_FOR_WAKE_WORD, LISTENING_TO_QUESTION, THINKING }

data class VoiceAssistantBubble(
    val text: String,
    val isAnswer: Boolean = false,
    val readThrough: Int = 0,
    val currentStart: Int = -1,
    val currentEnd: Int = -1,
    val isSpeaking: Boolean = false
)

/**
 * Experimental in-app wake phrase listener. It is composed only while Axognition
 * is visible, and is stopped when the parent turns the switch off.
 */
@Composable
fun WakeWordAssistant(
    enabled: Boolean,
    conversation: () -> List<ChatMessage>,
    onMessage: (ChatMessage) -> Unit,
    content: @Composable (VoiceAssistantBubble?, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    val saveMessage by rememberUpdatedState(onMessage)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var mode by remember { mutableStateOf(VoiceAssistantMode.IDLE) }
    var bubble by remember { mutableStateOf<VoiceAssistantBubble?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    var wakePhraseDetected by remember { mutableStateOf(false) }
    var restartAfterSpeech by remember { mutableStateOf(false) }
    var speechGeneration by remember { mutableStateOf(0) }
    val speakerReady = remember { AtomicBoolean(false) }
    val utteranceOffsets = remember { ConcurrentHashMap<String, Int>() }
    val utteranceEnds = remember { ConcurrentHashMap<String, Int>() }

    val wakeWordIntent = remember(AppLanguage.code) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Let the child pause naturally after the wake phrase or while thinking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        }
    }
    val questionIntent = remember(AppLanguage.code) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_350L)
        }
    }

    fun startWakeWordListening(delayMillis: Long = 450) {
        if (!latestEnabled || mode == VoiceAssistantMode.THINKING) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            delay(delayMillis)
            if (latestEnabled && mode != VoiceAssistantMode.THINKING) {
                wakePhraseDetected = false
                mode = VoiceAssistantMode.WAITING_FOR_WAKE_WORD
                runCatching { recognizer?.startListening(wakeWordIntent) }
                    .onFailure {
                        bubble = VoiceAssistantBubble("I could not start listening. Check the microphone permission.")
                    }
            }
        }
    }

    fun resumeWakeWordListening(clearBubble: Boolean = true) {
        restartAfterSpeech = false
        if (clearBubble) bubble = null
        mode = VoiceAssistantMode.IDLE
        startWakeWordListening(500)
    }

    fun askAssistant(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            bubble = VoiceAssistantBubble("I did not catch that. Say “Hej AI” and try again.")
            startWakeWordListening(1_500)
            return
        }
        mode = VoiceAssistantMode.THINKING
        val history = conversation().map { AssistantApi.HistoryMessage(it.text, it.fromStudent) }
        saveMessage(ChatMessage(cleanQuestion, true))
        bubble = VoiceAssistantBubble("Thinking…")
        restartAfterSpeech = false
        val generation = ++speechGeneration
        var receivedText = ""
        var queuedCount = 0
        val chunker = StreamingSpeechChunker { chunk, responseOffset ->
            val utteranceId = "wake-word-answer-$generation-$queuedCount"
            val queueMode = if (queuedCount++ == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            utteranceOffsets[utteranceId] = responseOffset
            utteranceEnds[utteranceId] = responseOffset + chunk.length
            mainHandler.post {
                if (generation != speechGeneration) return@post
                bubble = (bubble ?: VoiceAssistantBubble(receivedText, isAnswer = true)).copy(
                    text = receivedText,
                    isAnswer = true,
                    isSpeaking = true
                )
                val result = speaker?.speakInAppLanguage(
                    chunk,
                    utteranceId,
                    queueMode = queueMode,
                    preferNetwork = false,
                    naturalize = false
                )
                if (result != TextToSpeech.SUCCESS) {
                    utteranceOffsets.remove(utteranceId)
                    utteranceEnds.remove(utteranceId)
                }
            }
        }
        scope.launch {
            val answer = runCatching {
                withContext(Dispatchers.IO) {
                    AssistantApi.streamQuestion(cleanQuestion, history) { partial ->
                        receivedText = partial
                        mainHandler.post {
                            if (generation == speechGeneration) {
                                val current = bubble
                                bubble = VoiceAssistantBubble(
                                    text = partial,
                                    isAnswer = true,
                                    readThrough = current?.readThrough ?: 0,
                                    currentStart = current?.currentStart ?: -1,
                                    currentEnd = current?.currentEnd ?: -1,
                                    isSpeaking = current?.isSpeaking ?: false
                                )
                            }
                        }
                        chunker.accept(partial)
                    }
                }
            }.getOrElse { error ->
                if (receivedText.isNotBlank()) receivedText else runCatching {
                    withContext(Dispatchers.IO) { AssistantApi.sendQuestion(cleanQuestion, history) }
                }.getOrElse {
                    tr("I could not reach the learning assistant. ${tr(error.message ?: "Please try again.")}")
                }
            }
            receivedText = answer
            bubble = (bubble ?: VoiceAssistantBubble(answer, isAnswer = true)).copy(text = answer, isAnswer = true)
            chunker.accept(answer, final = true)
            saveMessage(ChatMessage(answer, false))
            restartAfterSpeech = true
            mainHandler.post {
                if (generation != speechGeneration) return@post
                if (queuedCount > 0 && speakerReady.get()) {
                    speaker?.playSilentUtterance(1L, TextToSpeech.QUEUE_ADD, "wake-word-finish-$generation")
                } else {
                    bubble = bubble?.copy(readThrough = answer.length, isSpeaking = false)
                    resumeWakeWordListening(clearBubble = false)
                }
            }
            delay(45_000)
            if (restartAfterSpeech && speechGeneration == generation) {
                bubble = bubble?.copy(readThrough = answer.length, currentStart = -1, currentEnd = -1, isSpeaking = false)
                resumeWakeWordListening(clearBubble = false)
            }
        }
    }

    fun beginQuestionListening() {
        if (!latestEnabled) return
        mode = VoiceAssistantMode.LISTENING_TO_QUESTION
        bubble = VoiceAssistantBubble("Listening…")
        scope.launch {
            delay(150)
            if (latestEnabled && mode == VoiceAssistantMode.LISTENING_TO_QUESTION) {
                runCatching { recognizer?.startListening(questionIntent) }
            }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWakeWordListening(0) else {
            mode = VoiceAssistantMode.IDLE
            bubble = VoiceAssistantBubble("Microphone permission is needed for “Hej AI”.")
        }
    }

    DisposableEffect(context, AppLanguage.code) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (latestEnabled) bubble = VoiceAssistantBubble("Voice recognition is not available on this tablet.")
            onDispose { }
        } else {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        if (mode == VoiceAssistantMode.WAITING_FOR_WAKE_WORD) {
                            startWakeWordListening()
                        } else if (mode == VoiceAssistantMode.LISTENING_TO_QUESTION) {
                            bubble = VoiceAssistantBubble("I did not catch that. Say “Hej AI” and try again.")
                            mode = VoiceAssistantMode.IDLE
                            startWakeWordListening(1_500)
                        }
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        when (mode) {
                            VoiceAssistantMode.WAITING_FOR_WAKE_WORD -> {
                                if (wakePhraseDetected || containsWakePhrase(heard)) {
                                    wakePhraseDetected = false
                                    val question = questionAfterWakePhrase(heard)
                                    if (question.isBlank()) beginQuestionListening() else askAssistant(question)
                                } else {
                                    startWakeWordListening()
                                }
                            }
                            VoiceAssistantMode.LISTENING_TO_QUESTION -> askAssistant(heard)
                            else -> Unit
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        if (mode != VoiceAssistantMode.WAITING_FOR_WAKE_WORD) return
                        val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (containsWakePhrase(heard)) {
                            wakePhraseDetected = true
                            bubble = VoiceAssistantBubble("I’m listening…")
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
            }
            recognizer = speechRecognizer
            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
                recognizer = null
            }
        }
    }

    DisposableEffect(context) {
        lateinit var textToSpeech: TextToSpeech
        textToSpeech = createAppTextToSpeech(context) { status ->
            speakerReady.set(status == TextToSpeech.SUCCESS)
            if (status == TextToSpeech.SUCCESS) mainHandler.post {
                textToSpeech.configureNaturalAppVoice(preferNetwork = false)
                textToSpeech.playSilentUtterance(1L, TextToSpeech.QUEUE_FLUSH, "wake-word-warmup")
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    if (utteranceId.startsWith("wake-word-answer-")) mainHandler.post {
                        bubble = bubble?.copy(isSpeaking = true)
                    }
                }
                override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                    val responseOffset = utteranceOffsets[utteranceId] ?: return
                    mainHandler.post {
                        val textLength = bubble?.text?.length ?: return@post
                        val absoluteStart = (responseOffset + start).coerceIn(0, textLength)
                        val absoluteEnd = (responseOffset + end).coerceIn(absoluteStart, textLength)
                        bubble = bubble?.copy(
                            readThrough = absoluteEnd,
                            currentStart = absoluteStart,
                            currentEnd = absoluteEnd,
                            isSpeaking = true
                        )
                    }
                }
                override fun onDone(utteranceId: String) {
                    if (utteranceId.startsWith("wake-word-answer-")) {
                        val end = utteranceEnds.remove(utteranceId) ?: 0
                        utteranceOffsets.remove(utteranceId)
                        mainHandler.post {
                            bubble = bubble?.copy(
                                readThrough = maxOf(bubble?.readThrough ?: 0, end),
                                currentStart = -1,
                                currentEnd = -1
                            )
                        }
                    } else if (utteranceId.startsWith("wake-word-finish-")) {
                        mainHandler.post {
                            bubble = bubble?.copy(
                                readThrough = bubble?.text?.length ?: 0,
                                currentStart = -1,
                                currentEnd = -1,
                                isSpeaking = false
                            )
                            if (restartAfterSpeech) resumeWakeWordListening(clearBubble = false)
                        }
                    }
                }
                override fun onError(utteranceId: String) {
                    utteranceOffsets.remove(utteranceId)
                    utteranceEnds.remove(utteranceId)
                    if (utteranceId.startsWith("wake-word-finish-")) {
                        mainHandler.post {
                            bubble = bubble?.copy(currentStart = -1, currentEnd = -1, isSpeaking = false)
                            if (restartAfterSpeech) resumeWakeWordListening(clearBubble = false)
                        }
                    }
                }
            })
        }
        speaker = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            speakerReady.set(false)
            utteranceOffsets.clear()
            utteranceEnds.clear()
            speaker = null
        }
    }

    LaunchedEffect(enabled, AppLanguage.code) {
        if (!enabled) {
            recognizer?.cancel()
            mode = VoiceAssistantMode.IDLE
            bubble = null
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            bubble = VoiceAssistantBubble("Listening for “Hej AI”…")
            startWakeWordListening(0)
            scope.launch {
                delay(2_500)
                if (bubble?.text == "Listening for “Hej AI”…") bubble = null
            }
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    content(bubble) {
        speechGeneration++
        restartAfterSpeech = false
        speaker?.stop()
        resumeWakeWordListening()
    }
}

private fun containsWakePhrase(text: String): Boolean {
    // Speech services often transcribe “AI” as “A.I.”, “I”, or “ay”.
    val compact = text.lowercase().filter { it.isLetterOrDigit() }
    return compact.contains("hejai") || compact.contains("heyai") ||
        compact.contains("heji") || compact.contains("heyi") || compact.contains("heyay")
}

private fun questionAfterWakePhrase(text: String): String = text
    // Some speech engines return only “Hej” in the final transcription even
    // when the partial result contained the complete wake phrase.
    .replaceFirst(Regex("(?i)^\\s*(?:hej|hey)(?:[\\s,.:;-]+(?:a\\.?\\s*i|i|ay))?[\\s,.:;-]*"), "")
    .trim()

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
                .firstOrNull { it.range.last + 1 >= 24 }
                ?.let { it.range.last + 1 }
            val rawLength = when {
                sentenceBoundary != null -> sentenceBoundary
                remaining.length >= 96 -> remaining.lastIndexOf(' ', startIndex = 84)
                    .takeIf { it >= 48 }
                    ?.plus(1)
                    ?: return
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
