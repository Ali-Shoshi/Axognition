package com.example.axognition.ui

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** The anchor always identifies the robot, never the expanded pill's left edge. */
@Composable
internal fun FloatingAssistantButton(
    anchor: Offset,
    state: VoiceAssistantBubble?,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    onAssistantBoundsChanged: (android.graphics.Rect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reportBounds by rememberUpdatedState(onAssistantBoundsChanged)
    DisposableEffect(Unit) { onDispose { reportBounds(null) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing
        val leftMargin = maxOf(16.dp, with(density) { insets.getLeft(density, layoutDirection).toDp() })
        val rightMargin = maxOf(16.dp, with(density) { insets.getRight(density, layoutDirection).toDp() })
        val topMargin = maxOf(16.dp, with(density) { insets.getTop(density).toDp() })
        val bottomMargin = maxOf(16.dp, with(density) { insets.getBottom(density).toDp() })
        // Listening between turns leaves the button at the student's saved position.
        val bubble = state?.takeUnless { it.isListening || it.isComplete }
        val availableTextWidth = (containerWidth - leftMargin - rightMargin - 56.dp).coerceAtLeast(0.dp)
        val hasAnswer = bubble?.isAnswer == true
        val textWidth = minOf(if (hasAnswer) 460.dp else 220.dp, availableTextWidth)
        val questionWidth = if (bubble == null) 0.dp else if (hasAnswer) textWidth * .43f else textWidth
        val answerWidth = if (hasAnswer) textWidth - questionWidth else 0.dp
        val groupWidth = questionWidth + 56.dp + answerWidth
        val robotPosition = Offset(
            anchor.x.coerceIn(
                with(density) { (leftMargin + questionWidth).toPx() },
                with(density) { (containerWidth - rightMargin - 56.dp - answerWidth).toPx() }
                    .coerceAtLeast(with(density) { (leftMargin + questionWidth).toPx() })
            ),
            anchor.y.coerceIn(
                with(density) { topMargin.toPx() },
                with(density) { (containerHeight - bottomMargin - 56.dp).toPx() }
                    .coerceAtLeast(with(density) { topMargin.toPx() })
            )
        )
        var dragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableStateOf(Offset.Zero) }
        val latestMove by rememberUpdatedState(onMove)
        val latestMoveEnd by rememberUpdatedState(onMoveEnd)
        val latestPosition by rememberUpdatedState(robotPosition)
        val displayedPosition by animateOffsetAsState(
            robotPosition,
            // Expand directly inside the screen; animate only the trip back.
            animationSpec = if (dragging || bubble != null) snap() else tween(220),
            label = "assistantReturnPosition"
        )
        val questionWidthPx = with(density) { questionWidth.toPx() }
        Box(
            Modifier.width(groupWidth).height(56.dp)
                .offset { IntOffset((displayedPosition.x - questionWidthPx).roundToInt(), displayedPosition.y.roundToInt()) }
                .testTag("assistantPill")
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    reportBounds(android.graphics.Rect(
                        kotlin.math.floor(bounds.left).toInt(), kotlin.math.floor(bounds.top).toInt(),
                        kotlin.math.ceil(bounds.right).toInt(), kotlin.math.ceil(bounds.bottom).toInt()))
                }
        ) {
            if (bubble != null) {
                Surface(
                    Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, if (bubble.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    shadowElevation = 5.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val question = when {
                            bubble.isQuestion -> bubble.text
                            bubble.question.isNotBlank() -> bubble.question
                            bubble.isAnswer -> ""
                            else -> tr(bubble.text)
                        }
                        AssistantPillText(
                            question, bubble, answer = false, onOpenChat,
                            Modifier.width(questionWidth).padding(start = 14.dp, end = 8.dp).testTag("assistantQuestion")
                        )
                        Spacer(Modifier.width(56.dp))
                        if (hasAnswer) {
                            Row(Modifier.width(answerWidth), verticalAlignment = Alignment.CenterVertically) {
                                AssistantPillText(
                                    bubble.text, bubble, answer = true, onOpenChat,
                                    Modifier.weight(1f).padding(start = 8.dp, end = 2.dp).testTag("assistantAnswer")
                                )
                                IconButton(onDismiss, Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, tr("Stop speaking and dismiss"), Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            AssistantChatButton(
                onClick = onOpenChat, speaking = bubble?.isSpeaking == true,
                modifier = Modifier.offset { IntOffset(questionWidthPx.roundToInt(), 0) }
                    .zIndex(1f).testTag("assistantRobot")
                    .pointerInput(containerWidth, containerHeight, questionWidth, answerWidth, leftMargin, rightMargin, topMargin, bottomMargin) {
                        detectDragGestures(
                            onDragStart = { dragging = true; dragPosition = latestPosition },
                            onDragEnd = { dragging = false; latestMoveEnd() },
                            onDragCancel = { dragging = false; latestMoveEnd() }
                        ) { change, amount ->
                            change.consume()
                            dragPosition = Offset(
                                (dragPosition.x + amount.x).coerceIn(
                                    with(density) { (leftMargin + questionWidth).toPx() },
                                    with(density) { (containerWidth - rightMargin - 56.dp - answerWidth).toPx() }
                                        .coerceAtLeast(with(density) { (leftMargin + questionWidth).toPx() })
                                ),
                                (dragPosition.y + amount.y).coerceIn(
                                    with(density) { topMargin.toPx() },
                                    with(density) { (containerHeight - bottomMargin - 56.dp).toPx() }
                                        .coerceAtLeast(with(density) { topMargin.toPx() })
                                )
                            )
                            latestMove(dragPosition)
                        }
                    }
            )
        }
    }
}

@Composable
private fun AssistantPillText(
    text: String,
    state: VoiceAssistantBubble,
    answer: Boolean,
    onOpenChat: () -> Unit,
    modifier: Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(text)
        if (answer) {
            val readEnd = state.readThrough.coerceIn(0, text.length)
            if (readEnd > 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, readEnd)
            val start = state.currentStart.coerceIn(0, text.length)
            val end = state.currentEnd.coerceIn(start, text.length)
            if (state.currentStart >= 0 && end > start) {
                addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = color, background = color.copy(alpha = .12f)), start, end)
            }
        }
    }
    val scroll = rememberScrollState()
    var lineStarts by remember(text) { mutableStateOf(emptyList<Int>()) }
    var lineTops by remember(text) { mutableStateOf(emptyList<Int>()) }
    val focus = when {
        !answer && state.isQuestion -> text.lastIndex
        answer && state.currentStart >= 0 -> state.currentStart
        answer && state.readThrough > 0 -> state.readThrough - 1
        else -> 0
    }.coerceIn(0, text.lastIndex.coerceAtLeast(0))
    LaunchedEffect(focus, lineStarts, lineTops) {
        val line = lineStarts.indexOfLast { it <= focus }.coerceAtLeast(0)
        if (lineTops.isNotEmpty()) scroll.animateScrollTo(lineTops[line.coerceAtMost((lineTops.lastIndex - 1).coerceAtLeast(0))], tween(180))
    }
    Box(modifier.height(44.dp).verticalScroll(scroll).clickable(onClick = onOpenChat), contentAlignment = Alignment.CenterStart) {
        Text(annotated, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), onTextLayout = { layout ->
            lineStarts = List(layout.lineCount) { layout.getLineStart(it) }
            lineTops = List(layout.lineCount) { layout.getLineTop(it).roundToInt() }
        })
    }
}
