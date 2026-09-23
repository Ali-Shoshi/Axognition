package com.example.axognition.ui.screens

import android.content.res.Configuration
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.axognition.ui.theme.LocalAxognitionDarkTheme
import com.example.axognition.ui.tr
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class EditorTool(val label: String, val icon: ImageVector) {
    Pen("Pen", Icons.Default.Edit),
    Highlighter("Marker", Icons.Default.Highlight),
    Eraser("Eraser", Icons.Default.AutoFixOff),
    Line("Line", Icons.Default.Remove),
    Square("Square", Icons.Default.CropSquare),
    Rectangle("Rectangle", Icons.Default.CropFree),
    Circle("Circle", Icons.Default.RadioButtonUnchecked),
    Arrow("Arrow", Icons.Default.ArrowForward)
}

private data class ActiveMark(
    val path: Path,
    val start: Offset,
    val end: Offset,
    val pointCount: Int,
    val color: Color,
    val width: Float,
    val tool: EditorTool,
    val canvasSize: Size
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(note: FileSystemItem.NoteFile, onBack: () -> Unit) {
    var pageIndex by remember(note.id) { mutableIntStateOf(note.selectedPageIndex.coerceIn(0, note.pages.lastIndex)) }
    val page = note.pages[pageIndex]
    var isDrawing by remember(note.id) { mutableStateOf(note.isDrawingMode) }
    val primaryInk = if (LocalAxognitionDarkTheme.current) Color.White else Color(0xFF28262D)
    var selectedColor by remember(note.id) { mutableStateOf(primaryInk) }
    var previousPrimaryInk by remember(note.id) { mutableStateOf(primaryInk) }
    var strokeWidth by remember(note.id) { mutableFloatStateOf(6f) }
    var eraserWidth by remember(note.id) { mutableFloatStateOf(32f) }
    var tool by remember(note.id) { mutableStateOf(EditorTool.Pen) }
    var stylusEraserHeld by remember(note.id) { mutableStateOf(false) }
    var gestureEraserAtDown by remember(note.id) { mutableStateOf(false) }
    var activeMark by remember(note.id) { mutableStateOf<ActiveMark?>(null) }
    val redoMarks = remember(note.id, pageIndex) { mutableStateListOf<DrawingLine>() }
    var showClearDialog by remember(note.id) { mutableStateOf(false) }
    var showDeleteDialog by remember(note.id) { mutableStateOf(false) }
    var showPageMenu by remember(note.id) { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveTool = if (stylusEraserHeld) EditorTool.Eraser else tool

    LaunchedEffect(primaryInk) {
        if (selectedColor == previousPrimaryInk) selectedColor = primaryInk
        previousPrimaryInk = primaryInk
    }

    fun finishMark() {
        activeMark?.let { mark ->
            if (mark.pointCount > 1 || mark.tool in listOf(EditorTool.Pen, EditorTool.Highlighter, EditorTool.Eraser)) {
                page.drawingPaths += DrawingLine(
                    path = mark.path,
                    color = mark.color,
                    strokeWidth = mark.width,
                    isEraser = mark.tool == EditorTool.Eraser,
                    isDot = mark.pointCount == 1,
                    dotPosition = mark.end,
                    canvasSize = mark.canvasSize
                )
                redoMarks.clear()
            }
        }
        activeMark = null
    }

    fun selectPage(index: Int) {
        finishMark()
        pageIndex = index
        note.selectedPageIndex = index
    }

    fun closeEditor() {
        finishMark()
        note.isDrawingMode = isDrawing
        onBack()
    }

    BackHandler(onBack = ::closeEditor)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(note.name.removeSuffix(".txt"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr("Page ${pageIndex + 1} of ${note.pages.size}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = ::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Save and go back")) } },
                actions = {
                    IconButton(onClick = { isDrawing = !isDrawing; note.isDrawingMode = isDrawing }) {
                        Icon(if (isDrawing) Icons.Default.Keyboard else Icons.Default.Edit, tr(if (isDrawing) "Switch to typing" else "Switch to drawing"))
                    }
                    Box {
                        IconButton(onClick = { showPageMenu = true }) { Icon(Icons.Default.MoreVert, tr("Page options")) }
                        DropdownMenu(expanded = showPageMenu, onDismissRequest = { showPageMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Duplicate page")) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = {
                                    finishMark()
                                    val copy = NotePage(page.textContent).also { it.drawingPaths.addAll(page.drawingPaths) }
                                    note.pages.add(pageIndex + 1, copy)
                                    pageIndex++
                                    note.selectedPageIndex = pageIndex
                                    showPageMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Delete page")) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                enabled = note.pages.size > 1,
                                onClick = { showPageMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                if (isDrawing) {
                    val ruleColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                        Canvas(Modifier.fillMaxSize()) {
                            val spacing = 30.dp.toPx()
                            var y = spacing
                            while (y < size.height) {
                                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                y += spacing
                            }
                        }
                        if (page.textContent.isNotBlank()) {
                            Text(
                                page.textContent,
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Canvas(
                            Modifier.fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .motionEventSpy { motion ->
                                    val eraserActive = motion.isStylusEraserActive()
                                    if (motion.actionMasked == MotionEvent.ACTION_DOWN) {
                                        gestureEraserAtDown = eraserActive
                                    }
                                    stylusEraserHeld = eraserActive
                                }
                                .pointerInput(pageIndex, tool, selectedColor, strokeWidth, eraserWidth) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val markTool = if (gestureEraserAtDown) EditorTool.Eraser else tool
                                            activeMark = ActiveMark(
                                                path = Path().apply { moveTo(start.x, start.y) },
                                                start = start,
                                                end = start,
                                                pointCount = 1,
                                                color = if (markTool == EditorTool.Highlighter) selectedColor.copy(alpha = 0.4f) else selectedColor,
                                                width = when (markTool) {
                                                    EditorTool.Eraser -> eraserWidth
                                                    EditorTool.Highlighter -> strokeWidth * 2.4f
                                                    else -> strokeWidth
                                                },
                                                tool = markTool,
                                                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeMark?.let { mark ->
                                                val end = change.position
                                                val path = if (mark.tool in listOf(EditorTool.Pen, EditorTool.Highlighter, EditorTool.Eraser)) {
                                                    mark.path.apply { lineTo(end.x, end.y) }
                                                } else shapePath(mark.tool, mark.start, end)
                                                activeMark = mark.copy(path = path, end = end, pointCount = mark.pointCount + 1)
                                            }
                                        },
                                        onDragEnd = ::finishMark,
                                        onDragCancel = ::finishMark
                                    )
                                }
                                .pointerInput(pageIndex, tool, selectedColor, strokeWidth, eraserWidth) {
                                    detectTapGestures { point ->
                                        val markTool = if (gestureEraserAtDown) EditorTool.Eraser else tool
                                        if (markTool in listOf(EditorTool.Pen, EditorTool.Highlighter, EditorTool.Eraser)) {
                                            page.drawingPaths += DrawingLine(
                                                path = Path(),
                                                color = if (markTool == EditorTool.Highlighter) selectedColor.copy(alpha = 0.4f) else selectedColor,
                                                strokeWidth = when (markTool) {
                                                    EditorTool.Eraser -> eraserWidth
                                                    EditorTool.Highlighter -> strokeWidth * 2.4f
                                                    else -> strokeWidth
                                                },
                                                isEraser = markTool == EditorTool.Eraser,
                                                isDot = true,
                                                dotPosition = point,
                                                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                                            )
                                            redoMarks.clear()
                                        }
                                    }
                                }
                        ) {
                            page.drawingPaths.forEach(::drawDrawingLine)
                            activeMark?.let { mark ->
                                drawDrawingLine(DrawingLine(mark.path, mark.color, mark.width, mark.tool == EditorTool.Eraser, mark.pointCount == 1, mark.end, mark.canvasSize))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = page.textContent,
                        onValueChange = { page.textContent = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        placeholder = { Text(tr("Start writing your note…")) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                    )
                }
            }

            if (isDrawing) {
                DrawingToolbar(
                    tool = effectiveTool,
                    isLandscape = isLandscape,
                    selectedColor = selectedColor,
                    primaryInk = primaryInk,
                    strokeWidth = if (effectiveTool == EditorTool.Eraser) eraserWidth else strokeWidth,
                    maxStrokeWidth = if (effectiveTool == EditorTool.Eraser) 160f else 32f,
                    canUndo = page.drawingPaths.isNotEmpty(),
                    canRedo = redoMarks.isNotEmpty(),
                    onToolSelected = { tool = it },
                    onColorSelected = { selectedColor = it; if (tool == EditorTool.Eraser) tool = EditorTool.Pen },
                    onStrokeWidthChanged = {
                        if (effectiveTool == EditorTool.Eraser) eraserWidth = it else strokeWidth = it
                    },
                    onUndo = { if (page.drawingPaths.isNotEmpty()) redoMarks += page.drawingPaths.removeAt(page.drawingPaths.lastIndex) },
                    onRedo = { if (redoMarks.isNotEmpty()) page.drawingPaths += redoMarks.removeAt(redoMarks.lastIndex) },
                    onClear = { showClearDialog = true }
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(note.pages.size) { index ->
                    val selected = index == pageIndex
                    Surface(
                        modifier = Modifier.size(width = 42.dp, height = 36.dp).clickable { selectPage(index) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier.size(width = 42.dp, height = 36.dp).clickable {
                            finishMark()
                            note.pages.add(pageIndex + 1, NotePage())
                            pageIndex++
                            note.selectedPageIndex = pageIndex
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, tr("Add page"), Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(tr("Clear drawing?")) },
            text = { Text(tr("This removes all strokes and shapes from this page.")) },
            confirmButton = { TextButton(onClick = { page.drawingPaths.clear(); redoMarks.clear(); activeMark = null; showClearDialog = false }) { Text(tr("Clear")) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(tr("Cancel")) } }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(tr("Delete page?")) },
            text = { Text(tr("This removes the text and drawing on this page.")) },
            confirmButton = {
                TextButton(onClick = {
                    if (note.pages.size > 1) {
                        finishMark()
                        note.pages.removeAt(pageIndex)
                        pageIndex = pageIndex.coerceAtMost(note.pages.lastIndex)
                        note.selectedPageIndex = pageIndex
                    }
                    showDeleteDialog = false
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
private fun DrawingToolbar(
    tool: EditorTool,
    isLandscape: Boolean,
    selectedColor: Color,
    primaryInk: Color,
    strokeWidth: Float,
    maxStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (EditorTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit
) {
    val colors = listOf(primaryInk, Color(0xFF3565D6), Color(0xFF008A68), Color(0xFFE25148), Color(0xFF8851C7), Color(0xFFF2AD35))
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(EditorTool.entries) { candidate ->
                    CompactToolButton(candidate, tool == candidate) { onToolSelected(candidate) }
                }
                if (isLandscape) {
                    item { Spacer(Modifier.width(6.dp)) }
                    item { CompactActionButton(Icons.Default.Undo, "Undo", canUndo, onUndo) }
                    item { CompactActionButton(Icons.Default.Redo, "Redo", canRedo, onRedo) }
                    item { CompactWidthControl(strokeWidth, maxStrokeWidth, onStrokeWidthChanged) }
                    items(colors) { color -> CompactColorButton(color, selectedColor == color && tool != EditorTool.Eraser) { onColorSelected(color) } }
                    item { CompactActionButton(Icons.Default.DeleteSweep, "Clear drawing", true, onClear) }
                }
            }
            if (!isLandscape) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item { CompactActionButton(Icons.Default.Undo, "Undo", canUndo, onUndo) }
                    item { CompactActionButton(Icons.Default.Redo, "Redo", canRedo, onRedo) }
                    item { CompactWidthControl(strokeWidth, maxStrokeWidth, onStrokeWidthChanged) }
                    items(colors) { color -> CompactColorButton(color, selectedColor == color && tool != EditorTool.Eraser) { onColorSelected(color) } }
                    item { CompactActionButton(Icons.Default.DeleteSweep, "Clear drawing", true, onClear) }
                }
            }
        }
    }
}

@Composable
private fun CompactToolButton(candidate: EditorTool, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClickLabel = tr(candidate.label), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(candidate.icon, tr(candidate.label), Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactColorButton(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(23.dp).clip(CircleShape).background(color)
                .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = Color.White)
        }
    }
}

@Composable
private fun CompactWidthControl(value: Float, maximum: Float, onChange: (Float) -> Unit) {
    Row(Modifier.width(132.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${value.toInt()}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
        Slider(value, onChange, Modifier.weight(1f), valueRange = 1f..maximum)
    }
}

@Composable
private fun CompactActionButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClickLabel = tr(description), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, tr(description), Modifier.size(21.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The side button and a stylus eraser tip temporarily override the selected drawing tool. */
private fun MotionEvent.isStylusEraserActive(): Boolean {
    if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) return false
    var stylusPresent = false
    for (index in 0 until pointerCount) {
        when (getToolType(index)) {
            MotionEvent.TOOL_TYPE_ERASER -> return true
            MotionEvent.TOOL_TYPE_STYLUS -> stylusPresent = true
        }
    }
    val stylusButtons = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_SECONDARY
    return stylusPresent && (buttonState and stylusButtons) != 0
}

private fun shapePath(tool: EditorTool, start: Offset, end: Offset): Path = Path().apply {
    val bounds = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    when (tool) {
        EditorTool.Line -> { moveTo(start.x, start.y); lineTo(end.x, end.y) }
        EditorTool.Square -> {
            val side = min(abs(end.x - start.x), abs(end.y - start.y))
            val corner = Offset(start.x + if (end.x >= start.x) side else -side, start.y + if (end.y >= start.y) side else -side)
            addRect(Rect(min(start.x, corner.x), min(start.y, corner.y), max(start.x, corner.x), max(start.y, corner.y)))
        }
        EditorTool.Rectangle -> addRect(bounds)
        EditorTool.Circle -> addOval(bounds)
        EditorTool.Arrow -> {
            moveTo(start.x, start.y)
            lineTo(end.x, end.y)
            val angle = atan2(end.y - start.y, end.x - start.x)
            val length = min(32f, (end - start).getDistance() * 0.35f)
            moveTo(end.x, end.y)
            lineTo(end.x - length * cos(angle - 0.55f), end.y - length * sin(angle - 0.55f))
            moveTo(end.x, end.y)
            lineTo(end.x - length * cos(angle + 0.55f), end.y - length * sin(angle + 0.55f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawDrawingLine(line: DrawingLine) {
    val paintColor = if (line.isEraser) Color.Transparent else line.color
    val blendMode = if (line.isEraser) BlendMode.Clear else BlendMode.SrcOver
    val scaleFactor = if (line.canvasSize.width > 0f && line.canvasSize.height > 0f) {
        min(size.width / line.canvasSize.width, size.height / line.canvasSize.height)
    } else 1f
    val x = if (line.canvasSize.width > 0f) (size.width - line.canvasSize.width * scaleFactor) / 2f else 0f
    val y = if (line.canvasSize.height > 0f) (size.height - line.canvasSize.height * scaleFactor) / 2f else 0f
    withTransform({ translate(x, y); scale(scaleFactor, scaleFactor, pivot = Offset.Zero) }) {
        if (line.isDot) {
            line.dotPosition?.let { drawCircle(paintColor, radius = line.strokeWidth / 2f, center = it, blendMode = blendMode) }
        } else {
            drawPath(
                line.path, paintColor,
                style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode
            )
        }
    }
}
