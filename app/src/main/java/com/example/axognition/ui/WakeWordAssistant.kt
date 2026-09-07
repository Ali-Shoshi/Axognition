package com.example.axognition.ui

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.axognition.data.AssistantApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VoiceAssistantMode { IDLE, WAITING_FOR_WAKE_WORD, LISTENING_TO_QUESTION, THINKING }

/**
 * Experimental in-app wake phrase listener. It is composed only while Axognition
 * is visible, and is stopped when the parent turns the switch off.
 */
@Composable
fun WakeWordAssistant(
    enabled: Boolean,
    conversation: () -> List<ChatMessage>,
    onMessage: (ChatMessage) -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    val saveMessage by rememberUpdatedState(onMessage)
    val openChat by rememberUpdatedState(onOpenChat)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var mode by remember { mutableStateOf(VoiceAssistantMode.IDLE) }
    var bubbleText by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    var wakePhraseDetected by remember { mutableStateOf(false) }
    var restartAfterSpeech by remember { mutableStateOf(false) }
    var speechGeneration by remember { mutableStateOf(0) }

    val wakeWordIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Let the child pause naturally after the wake phrase or while thinking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        }
    }
    val questionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
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
                        bubbleText = "I could not start listening. Check the microphone permission."
                    }
            }
        }
    }

    fun resumeWakeWordListening() {
        restartAfterSpeech = false
        bubbleText = null
        mode = VoiceAssistantMode.IDLE
        startWakeWordListening(500)
    }

    fun askAssistant(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            bubbleText = "I did not catch that. Say “Hej AI” and try again."
            startWakeWordListening(1_500)
            return
        }
        mode = VoiceAssistantMode.THINKING
        val history = conversation().map { AssistantApi.HistoryMessage(it.text, it.fromStudent) }
        saveMessage(ChatMessage(cleanQuestion, true))
        bubbleText = "Thinking…"
        scope.launch {
            val answer = runCatching {
                withContext(Dispatchers.IO) { AssistantApi.sendQuestion(cleanQuestion, history) }
            }.getOrElse { error ->
                "I could not reach the learning assistant. ${error.message ?: "Please try again."}"
            }
            bubbleText = answer
            saveMessage(ChatMessage(answer, false))
            restartAfterSpeech = true
            val generation = ++speechGeneration
            val speechResult = speaker?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "wake-word-answer")
            if (speechResult != TextToSpeech.SUCCESS) {
                delay(1_000)
                resumeWakeWordListening()
            } else {
                // Safety fallback in case a device does not report speech completion.
                delay(45_000)
                if (restartAfterSpeech && speechGeneration == generation) resumeWakeWordListening()
            }
        }
    }

    fun beginQuestionListening() {
        if (!latestEnabled) return
        mode = VoiceAssistantMode.LISTENING_TO_QUESTION
        bubbleText = "Listening…"
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
            bubbleText = "Microphone permission is needed for “Hej AI”."
        }
    }

    DisposableEffect(context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (latestEnabled) bubbleText = "Voice recognition is not available on this tablet."
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
                            bubbleText = "I did not catch that. Say “Hej AI” and try again."
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
                            bubbleText = "I’m listening…"
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
        val textToSpeech = TextToSpeech(context) { }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    if (utteranceId == "wake-word-answer") {
                        mainHandler.post {
                            if (restartAfterSpeech) resumeWakeWordListening()
                        }
                    }
                }
                override fun onError(utteranceId: String) {
                    if (utteranceId == "wake-word-answer") {
                        mainHandler.post {
                            if (restartAfterSpeech) resumeWakeWordListening()
                        }
                    }
                }
            })
        }
        speaker = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            speaker = null
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            recognizer?.cancel()
            mode = VoiceAssistantMode.IDLE
            bubbleText = null
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            bubbleText = "Listening for “Hej AI”…"
            startWakeWordListening(0)
            scope.launch {
                delay(2_500)
                if (bubbleText == "Listening for “Hej AI”…") bubbleText = null
            }
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    bubbleText?.let { text ->
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 5.dp))
                    Text(
                        text = text,
                        modifier = Modifier.weight(1f).clickable { openChat() },
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = {
                        speechGeneration++
                        restartAfterSpeech = false
                        speaker?.stop()
                        resumeWakeWordListening()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Stop speaking and dismiss")
                    }
                }
            }
        }
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
