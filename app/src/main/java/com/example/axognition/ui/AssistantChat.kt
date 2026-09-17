package com.example.axognition.ui

import com.example.axognition.ui.tr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.axognition.data.AssistantApi
import com.example.axognition.ui.createAppTextToSpeech
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class ChatMessage(val text: String, val fromStudent: Boolean)

@Composable
fun AssistantChatButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(56.dp),
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SmartToy, contentDescription = tr("Open learning assistant"))
        }
    }
}

@Composable
fun AssistantChatPanel(
    expanded: Boolean,
    anchor: Offset,
    onMove: (Offset) -> Unit,
    messages: List<ChatMessage>,
    onMessage: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
    listeningMode: VoiceListeningMode,
    onListeningModeChange: (VoiceListeningMode) -> Unit,
    onVoiceInput: () -> Unit,
    onAudioBusyChange: (Boolean) -> Unit,
    voiceStatus: VoiceAssistantBubble?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expansion by animateFloatAsState(if (expanded) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "assistantExpansion")
    val orientation = LocalConfiguration.current.orientation
    val sizePreferences = remember { context.getSharedPreferences("assistant_window", android.content.Context.MODE_PRIVATE) }
    var savedWidth by remember(orientation) { mutableStateOf(sizePreferences.getFloat("width_$orientation", 390f)) }
    var savedHeight by remember(orientation) { mutableStateOf(sizePreferences.getFloat("height_$orientation", -1f)) }
    LaunchedEffect(savedWidth, savedHeight, orientation) {
        sizePreferences.edit().putFloat("width_$orientation", savedWidth).putFloat("height_$orientation", savedHeight).apply()
    }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var speechRecognitionAvailable by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeechReady by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isReading by remember { mutableStateOf(false) }
    val audioBusyCallback by rememberUpdatedState(onAudioBusyChange)
    LaunchedEffect(isSending, isListening, isReading) {
        audioBusyCallback(isSending || isListening || isReading)
    }
    LaunchedEffect(listeningMode, expanded) {
        recognizer?.cancel()
        isListening = false
        speaker?.stop()
        isReading = false
    }

    val recognitionIntent = remember(AppLanguage.code) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun beginListening() {
        recognizer?.startListening(recognitionIntent)
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginListening()
    }

    DisposableEffect(context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognitionAvailable = false
            onDispose { }
        } else {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) { isListening = true }
                    override fun onBeginningOfSpeech() { isListening = true }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) { isListening = false }
                    override fun onResults(results: android.os.Bundle?) {
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.let { draft = it }
                        isListening = false
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.let { draft = it }
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
            }
            recognizer = speechRecognizer
            onDispose {
                speechRecognizer.destroy()
                recognizer = null
            }
        }
    }

    DisposableEffect(context) {
        val textToSpeech = createAppTextToSpeech(context) { status ->
            isSpeechReady = status == TextToSpeech.SUCCESS
        }
        textToSpeech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) { scope.launch { isReading = false } }
            @Deprecated("Android callback")
            override fun onError(id: String) { scope.launch { isReading = false } }
        })
        speaker = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
            speaker = null
            audioBusyCallback(false)
        }
    }

    LaunchedEffect(messages.size, voiceStatus?.text) {
        if (messages.isNotEmpty()) {
            val streaming = voiceStatus?.isAnswer == true &&
                !(messages.lastOrNull()?.fromStudent == false && messages.lastOrNull()?.text == voiceStatus.text)
            listState.animateScrollToItem(if (streaming) messages.size else messages.lastIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val edgeMarginPx = with(density) { 16.dp.toPx() }
        val maxPanelX = (containerWidthPx - panelSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val maxPanelY = (containerHeightPx - panelSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val panelPosition = Offset(anchor.x.coerceIn(edgeMarginPx, maxPanelX), anchor.y.coerceIn(edgeMarginPx, maxPanelY))
        val buttonPixels = with(density) { 56.dp.toPx() }

        fun movePanel(dragAmount: Offset) {
            val current = panelPosition
            onMove(Offset(
                x = (current.x + dragAmount.x).coerceIn(edgeMarginPx, maxPanelX),
                y = (current.y + dragAmount.y).coerceIn(edgeMarginPx, maxPanelY)
            ))
        }
        // Read the latest bounds without restarting a gesture on every movement.
        val dragPanel by rememberUpdatedState<(Offset) -> Unit>({ movePanel(it) })
        val resizePanel by rememberUpdatedState<(Float) -> Unit>({ zoom ->
            val maxWidthDp = (maxWidth.value - 32f).coerceAtLeast(1f)
            val maxHeightDp = (maxHeight.value - 32f).coerceAtLeast(1f)
            savedWidth = (with(density) { panelSize.width.toDp().value } * zoom)
                .coerceIn(minOf(320f, maxWidthDp), maxWidthDp)
            savedHeight = (with(density) { panelSize.height.toDp().value } * zoom)
                .coerceIn(minOf(if (messages.isEmpty()) 300f else 420f, maxHeightDp), maxHeightDp)
        })
        val resizeByDrag by rememberUpdatedState<(Offset) -> Unit>({ amount ->
            val widthLimit = (maxWidth.value - 32f).coerceAtLeast(1f)
            val heightLimit = (maxHeight.value - 32f).coerceAtLeast(1f)
            savedWidth = ((if (savedWidth > 0f) savedWidth else with(density) { panelSize.width.toDp().value }) + amount.x / density.density)
                .coerceIn(minOf(320f, widthLimit), widthLimit)
            savedHeight = ((if (savedHeight > 0f) savedHeight else with(density) { panelSize.height.toDp().value }) + amount.y / density.density)
                .coerceIn(minOf(if (messages.isEmpty()) 300f else 420f, heightLimit), heightLimit)
        })

        if (expanded || expansion > 0f) {
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(savedWidth.coerceIn(minOf(320f, (maxWidth.value - 32f).coerceAtLeast(1f)), (maxWidth.value - 32f).coerceAtLeast(1f)).dp)
                .then(
                    if (savedHeight > 0f) Modifier.height(savedHeight
                        .coerceAtLeast(if (messages.isEmpty()) 300f else 420f)
                        .coerceAtMost((maxHeight.value - 32f).coerceAtLeast(1f)).dp)
                    else if (messages.isEmpty()) Modifier else Modifier.fillMaxHeight(0.76f)
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom.isFinite() && zoom > 0f) resizePanel(zoom)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .onSizeChanged { panelSize = it }
                .offset {
                    val position = anchor + (panelPosition - anchor) * expansion
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = buttonPixels / panelSize.width.coerceAtLeast(1) * (1f - expansion) + expansion
                    scaleY = buttonPixels / panelSize.height.coerceAtLeast(1) * (1f - expansion) + expansion
                    alpha = expansion
                },
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                Modifier
                    .then(if (messages.isEmpty()) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                    .padding(18.dp)
            ) {
                if (messages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                dragPanel(dragAmount)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = tr("Drag assistant chat"),
                                modifier = Modifier.padding(9.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Learning assistant"), fontWeight = FontWeight.Bold)
                            Text(tr("Hold and drag here to move"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = tr("Close assistant"))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromStudent) Alignment.CenterEnd else Alignment.CenterStart) {
                                Surface(
                                    color = if (message.fromStudent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (message.fromStudent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 14.dp, top = 7.dp, bottom = 7.dp, end = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(message.text, modifier = Modifier.weight(1f))
                                        if (!message.fromStudent) {
                                            IconButton(onClick = {
                                                isReading = true
                                                scope.launch {
                                                    kotlinx.coroutines.delay(200)
                                                    if (isReading && speaker?.speakInAppLanguage(message.text, "assistant-response-${message.hashCode()}") != TextToSpeech.SUCCESS) isReading = false
                                                }
                                            }, enabled = isSpeechReady, modifier = Modifier.size(40.dp)) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = tr("Read this response aloud"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (voiceStatus?.isAnswer == true &&
                            !(messages.lastOrNull()?.fromStudent == false && messages.lastOrNull()?.text == voiceStatus.text)) {
                            item {
                                Surface(color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shape = RoundedCornerShape(18.dp)) {
                                    Text(voiceStatus.text, modifier = Modifier.padding(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Ask a question…"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    dragPanel(dragAmount)
                                }
                            }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("Shrink assistant"))
                    }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (voiceStatus?.isQuestion == true) {
                    val transcriptScroll = rememberScrollState()
                    LaunchedEffect(voiceStatus.text, transcriptScroll.maxValue) {
                        transcriptScroll.animateScrollTo(transcriptScroll.maxValue)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(tr("Your question"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(voiceStatus.text, modifier = Modifier.heightIn(max = 88.dp).verticalScroll(transcriptScroll),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(tr("Ask a question…")) },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    ElevatedButton(
                        onClick = {
                            if (listeningMode != VoiceListeningMode.OFF) {
                                speaker?.stop()
                                isReading = false
                                scope.launch {
                                    kotlinx.coroutines.delay(200)
                                    onVoiceInput()
                                }
                            } else if (isListening) {
                                recognizer?.stopListening()
                                isListening = false
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                beginListening()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = speechRecognitionAvailable && !isSending,
                        modifier = Modifier.size(52.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (isListening || voiceStatus?.isSpeaking == true) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = tr(if (voiceStatus?.isSpeaking == true) "Interrupt and speak" else if (isListening) "Stop listening" else "Speak your question")
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ElevatedButton(
                        onClick = {
                            val question = draft.trim()
                            if (question.isNotEmpty()) {
                                val history = messages.map { AssistantApi.HistoryMessage(it.text, it.fromStudent) }
                                onMessage(ChatMessage(question, fromStudent = true))
                                draft = ""
                                isSending = true
                                scope.launch {
                                    val response = runCatching {
                                        withContext(Dispatchers.IO) { AssistantApi.sendQuestion(question, history) }
                                    }.getOrElse { error ->
                                        tr("I could not reach the learning assistant. ${tr(error.message ?: "Please try again.")}")
                                    }
                                    onMessage(ChatMessage(response, fromStudent = false))
                                    isSending = false
                                }
                            }
                        },
                        enabled = draft.isNotBlank() && !isSending,
                        modifier = Modifier.size(52.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Send question"))
                    }
                }
                if (isSending) {
                    Text(
                        tr("Thinking…"),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isListening) {
                    Text(
                        tr("Listening… speak now"),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        VoiceModeSelector(listeningMode, onListeningModeChange)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { resizePanel(0.9f) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = tr("Make AI chat smaller"))
                    }
                    IconButton(onClick = { resizePanel(1.1f) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, contentDescription = tr("Make AI chat bigger"))
                    }
                    Box(
                        Modifier.size(40.dp).pointerInput(Unit) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                resizeByDrag(amount)
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.OpenInFull, contentDescription = tr("Drag to resize AI chat"))
                    }
                }
                Text(
                    tr(if (voiceStatus?.isSpeaking == true) "Say “hej AI” to interrupt"
                        else if (voiceStatus != null && !voiceStatus.isAnswer && !voiceStatus.isQuestion) voiceStatus.text
                        else listeningMode.hint),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    }
}

/** Three stops with a sliding thumb; tap, drag, or select with accessibility tools. */
@Composable
internal fun VoiceModeSelector(mode: VoiceListeningMode, onSelect: (VoiceListeningMode) -> Unit) {
    val position by animateFloatAsState(mode.ordinal.toFloat(), tween(220), label = "voiceMode")
    val latestSelect by rememberUpdatedState(onSelect)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .selectableGroup()
    ) {
        val segmentWidth = maxWidth / 3
        val density = LocalDensity.current
        val segmentPixels = with(density) { segmentWidth.toPx() }
        Box(Modifier.offset(x = segmentWidth * position).width(segmentWidth).fillMaxHeight()
            .padding(3.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary))
        Row(Modifier.fillMaxSize().pointerInput(segmentPixels) {
            detectDragGestures { change, _ ->
                change.consume()
                val index = (change.position.x / segmentPixels).toInt().coerceIn(0, 2)
                latestSelect(VoiceListeningMode.entries[index])
            }
        }) {
            VoiceListeningMode.entries.forEach { item ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = mode == item, role = Role.RadioButton, onClick = { onSelect(item) }
                    ).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr(item.label), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (mode == item) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (mode == item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
