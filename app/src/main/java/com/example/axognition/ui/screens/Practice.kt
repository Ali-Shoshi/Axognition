package com.example.axognition.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val strokeWidth: Float
)

@Composable
fun PracticeScreen(onBack: () -> Unit) {
    // Root directory state
    val rootDirectory = remember {
        mutableStateOf(
            FileSystemItem.Folder(
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
        )
    }

    var currentFolder by remember { mutableStateOf<FileSystemItem.Folder>(rootDirectory.value) }
    // Fixed: Removed explicit type argument to let Kotlin infer it cleanly
    var folderNavStack by remember { mutableStateOf(emptyList<FileSystemItem.Folder>()) }
    var activeNote by remember { mutableStateOf<FileSystemItem.NoteFile?>(null) }

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
                onBack = { activeNote = null }
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
                                folderNavStack = folderNavStack.dropLast(1)
                                currentFolder = prev
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
                                            folderNavStack = folderNavStack + currentFolder
                                            currentFolder = item
                                        }
                                        is FileSystemItem.NoteFile -> {
                                            activeNote = item
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

@Composable
fun NoteEditorScreen(note: FileSystemItem.NoteFile, onBack: () -> Unit) {
    var isDrawing by remember { mutableStateOf(note.isDrawingMode) }
    var textContent by remember { mutableStateOf(note.textContent) }

    // Drawing Tool States
    var selectedColor by remember { mutableStateOf(Color.Black) }
    // Fixed: Used mutableFloatStateOf instead of mutableStateOf for optimal primitive tracking
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    var isEraser by remember { mutableStateOf(false) }

    val currentPath = remember { mutableStateOf<Path?>(null) }
    val paths = remember { mutableStateListOf<DrawingLine>().apply { addAll(note.drawingPaths) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                note.textContent = textContent
                note.drawingPaths.clear()
                note.drawingPaths.addAll(paths)
                note.isDrawingMode = isDrawing
                onBack()
            }) {
                // Fixed: Updated to AutoMirrored ArrowBack
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and Back")
            }

            Text(text = note.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Material 3 Segmented Button Row
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    onClick = { isDrawing = false },
                    selected = !isDrawing
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    onClick = { isDrawing = true },
                    selected = isDrawing
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Pen", modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pen Tools Toolbar (Visible only in drawing mode)
        if (isDrawing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colors
                        val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Magenta)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colors) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            selectedColor = color
                                            isEraser = false
                                        }
                                        .border(
                                            width = if (selectedColor == color && !isEraser) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            }
                            item {
                                // Eraser button
                                IconButton(onClick = { isEraser = true }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Eraser",
                                        tint = if (isEraser) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }
                        }

                        // Clear Canvas Button
                        IconButton(onClick = { paths.clear() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Stroke Width Slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Width: ${strokeWidth.toInt()}", fontSize = 12.sp)
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 2f..40f,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Main Editor Surface (Text vs Canvas)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        ) {
            if (!isDrawing) {
                // Keyboard Typing Field
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("Start typing your notes here...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            } else {
                // Freehand Drawing Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(true) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath.value = newPath
                                },
                                onDrag = { change, _ ->
                                    val offset = change.position
                                    currentPath.value?.lineTo(offset.x, offset.y)
                                    currentPath.value = currentPath.value
                                },
                                onDragEnd = {
                                    currentPath.value?.let { path ->
                                        val paintColor = if (isEraser) Color.White else selectedColor
                                        paths.add(DrawingLine(path, paintColor, strokeWidth))
                                        currentPath.value = null
                                    }
                                }
                            )
                        }
                ) {
                    for (line in paths) {
                        drawPath(
                            path = line.path,
                            color = line.color,
                            style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    currentPath.value?.let { path ->
                        val paintColor = if (isEraser) Color.White else selectedColor
                        drawPath(
                            path = path,
                            color = paintColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }
    }
}
