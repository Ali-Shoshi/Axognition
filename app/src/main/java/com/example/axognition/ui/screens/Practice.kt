package com.example.axognition.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.axognition.ui.theme.LocalAxognitionDarkTheme

// --- Data Models for File System ---
sealed interface FileSystemItem {
    val id: String
    val name: String
    data class Folder(override val id: String, override val name: String, val items: MutableList<FileSystemItem> = mutableListOf()) : FileSystemItem
    data class NoteFile(
        override val id: String,
        override val name: String,
        var textContent: String = "",
        var drawingPaths: MutableList<DrawingLine> = mutableListOf(),
        var isDrawingMode: Boolean = false
    ) : FileSystemItem
}

data class DrawingLine(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val isDot: Boolean = false,
    val dotPosition: Offset? = null
)

private data class ActiveDrawingLine(
    val path: Path,
    val lastPoint: Offset,
    val pointCount: Int,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean
)

/** Keeps the currently-open notebook and its unsaved edits alive across configuration changes. */
class PracticeViewModel : ViewModel() {
    val rootDirectory = FileSystemItem.Folder(
        id = "root",
        name = "My Notebooks",
        items = mutableStateListOf(
            FileSystemItem.Folder(
                id = "f1",
                name = "Mathematics",
                items = mutableStateListOf(
                    FileSystemItem.NoteFile(id = "n1", name = "Algebra Practice.txt", textContent = "Quadratic equations notes...")
                )
            ),
            FileSystemItem.NoteFile(id = "n2", name = "Quick Ideas.txt", textContent = "Brainstorming new concepts...")
        )
    )
    var currentFolder by mutableStateOf(rootDirectory)
    var folderNavStack by mutableStateOf(emptyList<FileSystemItem.Folder>())
    var activeNote by mutableStateOf<FileSystemItem.NoteFile?>(null)
}

@Composable
fun PracticeScreen(onBack: () -> Unit) {
    val practiceViewModel: PracticeViewModel = viewModel()
    val currentFolder = practiceViewModel.currentFolder
    val folderNavStack = practiceViewModel.folderNavStack
    val activeNote = practiceViewModel.activeNote

    // Dialog states for creating new folders/files
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newEntityName by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (activeNote != null) {
            // --- Note Editor View (Keyboard + Pen Canvas) ---
            NoteEditorScreen(
                note = activeNote!!,
                onBack = { practiceViewModel.activeNote = null }
            )
        } else {
            // --- File System Explorer View ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (folderNavStack.isNotEmpty()) {
                            IconButton(onClick = {
                                val prev = folderNavStack.last()
                                practiceViewModel.folderNavStack = folderNavStack.dropLast(1)
                                practiceViewModel.currentFolder = prev
                            }) {
                                // Fixed: Updated to AutoMirrored ArrowBack
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            text = currentFolder.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons (New Folder, New File)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Folder")
                    }
                    OutlinedButton(onClick = { showCreateFileDialog = true }) {
                        // Fixed: Updated to AutoMirrored NoteAdd
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Note")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Items Listing
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentFolder.items) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (item) {
                                        is FileSystemItem.Folder -> {
                                            practiceViewModel.folderNavStack = folderNavStack + currentFolder
                                            practiceViewModel.currentFolder = item
                                        }
                                        is FileSystemItem.NoteFile -> {
                                            practiceViewModel.activeNote = item
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item is FileSystemItem.Folder) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (item is FileSystemItem.Folder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newEntityName,
                    onValueChange = { newEntityName = it },
                    placeholder = { Text("Folder Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEntityName.isNotBlank()) {
                        currentFolder.items.add(FileSystemItem.Folder(id = System.currentTimeMillis().toString(), name = newEntityName))
                        newEntityName = ""
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New Note") },
            text = {
                OutlinedTextField(
                    value = newEntityName,
                    onValueChange = { newEntityName = it },
                    placeholder = { Text("Note Name (e.g. Physics.txt)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEntityName.isNotBlank()) {
                        val fileName = if (newEntityName.endsWith(".txt")) newEntityName else "$newEntityName.txt"
                        currentFolder.items.add(FileSystemItem.NoteFile(id = System.currentTimeMillis().toString(), name = fileName))
                        newEntityName = ""
                        showCreateFileDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(note: FileSystemItem.NoteFile, onBack: () -> Unit) {
    var isDrawing by remember(note.id) { mutableStateOf(note.isDrawingMode) }
    var textContent by remember(note.id) { mutableStateOf(note.textContent) }
    val primaryInk = if (LocalAxognitionDarkTheme.current) Color.White else Color(0xFF28262D)
    var selectedColor by remember(note.id) { mutableStateOf(primaryInk) }
    var previousPrimaryInk by remember(note.id) { mutableStateOf(primaryInk) }
    var strokeWidth by remember(note.id) { mutableFloatStateOf(7f) }
    var isEraser by remember(note.id) { mutableStateOf(false) }
    var isHighlighter by remember(note.id) { mutableStateOf(false) }
    var showClearDrawingDialog by remember(note.id) { mutableStateOf(false) }

    val paths = remember(note.id) { mutableStateListOf<DrawingLine>().apply { addAll(note.drawingPaths) } }
    val redoPaths = remember(note.id) { mutableStateListOf<DrawingLine>() }
    // Reassigning this small wrapper redraws just the active stroke, without rebuilding saved paths.
    var activeStroke by remember(note.id) { mutableStateOf<ActiveDrawingLine?>(null) }

    LaunchedEffect(primaryInk) {
        if (selectedColor == previousPrimaryInk) selectedColor = primaryInk
        previousPrimaryInk = primaryInk
    }

    fun persistDrawing() {
        note.drawingPaths.clear()
        note.drawingPaths.addAll(paths)
    }

    fun finishStroke() {
        activeStroke?.let { stroke ->
            paths += DrawingLine(
                path = stroke.path,
                color = stroke.color,
                strokeWidth = stroke.strokeWidth,
                isEraser = stroke.isEraser,
                isDot = stroke.pointCount == 1,
                dotPosition = stroke.lastPoint
            )
            redoPaths.clear()
            persistDrawing()
        }
        activeStroke = null
    }

    fun saveAndExit() {
        finishStroke()
        note.textContent = textContent
        persistDrawing()
        note.isDrawingMode = isDrawing
        onBack()
    }

    BackHandler(onBack = ::saveAndExit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(note.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (isDrawing) "Drawing board" else "Text note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = ::saveAndExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Save and go back") } },
                actions = {
                    IconButton(onClick = { isDrawing = !isDrawing; note.isDrawingMode = isDrawing }) {
                        Icon(if (isDrawing) Icons.Default.Keyboard else Icons.Default.Edit, if (isDrawing) "Switch to typing" else "Switch to drawing")
                    }
                    IconButton(onClick = ::saveAndExit) { Icon(Icons.Default.Check, "Save note") }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (isDrawing) {
                DrawingTools(
                    primaryInk = primaryInk,
                    selectedColor = selectedColor,
                    strokeWidth = strokeWidth,
                    isEraser = isEraser,
                    isHighlighter = isHighlighter,
                    canUndo = paths.isNotEmpty(),
                    canRedo = redoPaths.isNotEmpty(),
                    onColorSelected = { selectedColor = it; isEraser = false },
                    onPenSelected = { isEraser = false; isHighlighter = false },
                    onHighlighterSelected = { isEraser = false; isHighlighter = true },
                    onEraserSelected = { isEraser = true; isHighlighter = false },
                    onStrokeWidthChanged = { strokeWidth = it },
                    onUndo = { if (paths.isNotEmpty()) { redoPaths += paths.removeAt(paths.lastIndex); persistDrawing() } },
                    onRedo = { if (redoPaths.isNotEmpty()) { paths += redoPaths.removeAt(redoPaths.lastIndex); persistDrawing() } },
                    onClear = { showClearDrawingDialog = true }
                )
                Spacer(Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (!isDrawing) {
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it; note.textContent = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        placeholder = { Text("Start writing your note…") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                    )
                } else {
                    val canvasColor = MaterialTheme.colorScheme.surface
                    val ruleColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(canvasColor)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val spacing = 28.dp.toPx()
                            var y = spacing
                            while (y < size.height) {
                                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                y += spacing
                            }
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .pointerInput(selectedColor, strokeWidth, isEraser, isHighlighter) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            activeStroke = ActiveDrawingLine(
                                                path = Path().apply { moveTo(offset.x, offset.y) },
                                                lastPoint = offset,
                                                pointCount = 1,
                                                color = if (isHighlighter) selectedColor.copy(alpha = 0.38f) else selectedColor,
                                                strokeWidth = strokeWidth * if (isHighlighter) 1.55f else 1f,
                                                isEraser = isEraser
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStroke?.let { stroke ->
                                                val point = change.position
                                                stroke.path.quadraticTo(
                                                    stroke.lastPoint.x,
                                                    stroke.lastPoint.y,
                                                    point.x,
                                                    point.y
                                                )
                                                activeStroke = stroke.copy(lastPoint = point, pointCount = stroke.pointCount + 1)
                                            }
                                        },
                                        onDragEnd = ::finishStroke,
                                        onDragCancel = ::finishStroke
                                    )
                                }
                        ) {
                            paths.forEach(::drawDrawingLine)
                            activeStroke?.let { stroke ->
                                drawDrawingLine(
                                    DrawingLine(
                                        path = stroke.path,
                                        color = stroke.color,
                                        strokeWidth = stroke.strokeWidth,
                                        isEraser = stroke.isEraser,
                                        isDot = stroke.pointCount == 1,
                                        dotPosition = stroke.lastPoint
                                    )
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Text("${paths.size} strokes", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showClearDrawingDialog) {
        AlertDialog(
            onDismissRequest = { showClearDrawingDialog = false },
            title = { Text("Clear drawing?") },
            text = { Text("This removes all strokes from this page.") },
            confirmButton = {
                TextButton(onClick = { paths.clear(); redoPaths.clear(); activeStroke = null; persistDrawing(); showClearDrawingDialog = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDrawingDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DrawingTools(
    primaryInk: Color,
    selectedColor: Color,
    strokeWidth: Float,
    isEraser: Boolean,
    isHighlighter: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onColorSelected: (Color) -> Unit,
    onPenSelected: () -> Unit,
    onHighlighterSelected: () -> Unit,
    onEraserSelected: () -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit
) {
    val colors = listOf(primaryInk, Color(0xFF375BDB), Color(0xFF008A68), Color(0xFFE65A3F), Color(0xFF9B51E0), Color(0xFFF2B134))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Undo, "Undo", modifier = Modifier.size(20.dp))
                }
            }
            item {
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Redo, "Redo", modifier = Modifier.size(20.dp))
                }
            }
            items(colors) { color ->
                Box(
                    Modifier
                        .size(27.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (selectedColor == color && !isEraser) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onColorSelected(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == color && !isEraser) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            item {
                CompactDrawingToolButton(
                    selected = !isEraser && !isHighlighter,
                    onClick = onPenSelected,
                    icon = Icons.Default.Edit,
                    description = "Pen"
                )
            }
            item {
                CompactDrawingToolButton(
                    selected = isHighlighter,
                    onClick = onHighlighterSelected,
                    icon = Icons.Default.Highlight,
                    description = "Marker"
                )
            }
            item {
                CompactDrawingToolButton(
                    selected = isEraser,
                    onClick = onEraserSelected,
                    icon = Icons.Default.AutoFixOff,
                    description = "Eraser"
                )
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onStrokeWidthChanged((strokeWidth - 2f).coerceAtLeast(2f)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, "Decrease pen size", modifier = Modifier.size(18.dp))
                        }
                        Text("${strokeWidth.toInt()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { onStrokeWidthChanged((strokeWidth + 2f).coerceAtMost(72f)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, "Increase pen size", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        modifier = Modifier.width(176.dp).padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${strokeWidth.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Slider(
                            value = strokeWidth,
                            onValueChange = onStrokeWidthChanged,
                            valueRange = 1f..72f,
                            modifier = Modifier.padding(start = 8.dp).weight(1f)
                        )
                    }
                }
            }
            item {
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteSweep, "Clear drawing", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactDrawingToolButton(selected: Boolean, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
    ) {
        Icon(
            icon,
            description,
            modifier = Modifier.size(19.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawDrawingLine(line: DrawingLine) {
    val paintColor = if (line.isEraser) Color.Transparent else line.color
    val blendMode = if (line.isEraser) BlendMode.Clear else BlendMode.SrcOver
    val style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (line.isDot) {
        line.dotPosition?.let { point ->
            drawCircle(paintColor, radius = line.strokeWidth / 2f, center = point, blendMode = blendMode)
        }
    } else {
        drawPath(path = line.path, color = paintColor, style = style, blendMode = blendMode)
    }
}
