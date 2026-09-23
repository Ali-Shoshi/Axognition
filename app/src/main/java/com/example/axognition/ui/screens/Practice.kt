package com.example.axognition.ui.screens

import com.example.axognition.ui.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// --- Data Models for File System ---
sealed interface FileSystemItem {
    val id: String
    val name: String
    data class Folder(override val id: String, override val name: String, val items: MutableList<FileSystemItem> = mutableListOf()) : FileSystemItem
    data class NoteFile(
        override val id: String,
        override val name: String,
        val pages: MutableList<NotePage> = mutableStateListOf(NotePage()),
        var isDrawingMode: Boolean = false
    ) : FileSystemItem {
        var selectedPageIndex by mutableIntStateOf(0)
    }
}

class NotePage(initialText: String = "") {
    var textContent by mutableStateOf(initialText)
    val drawingPaths = mutableStateListOf<DrawingLine>()
}

data class DrawingLine(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val isDot: Boolean = false,
    val dotPosition: Offset? = null,
    val canvasSize: Size = Size.Zero
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
                    FileSystemItem.NoteFile(id = "n1", name = "Algebra Practice", pages = mutableStateListOf(NotePage("Quadratic equations notes...")))
                )
            ),
            FileSystemItem.NoteFile(id = "n2", name = "Quick Ideas", pages = mutableStateListOf(NotePage("Brainstorming new concepts...")))
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                            }
                        }
                        Text(
                            text = if (currentFolder.id == "root" || currentFolder.id == "f1") tr(currentFolder.name) else currentFolder.name,
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
                        Text(tr("New Folder"))
                    }
                    OutlinedButton(onClick = { showCreateFileDialog = true }) {
                        // Fixed: Updated to AutoMirrored NoteAdd
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("New Note"))
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
                            shape = RoundedCornerShape(16.dp),
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
                                Column {
                                    Text(
                                        text = if (item.id == "f1") tr(item.name) else item.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (item is FileSystemItem.NoteFile) {
                                        Text(
                                            text = tr("${item.pages.size} ${if (item.pages.size == 1) "page" else "pages"}"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
            title = { Text(tr("Create New Folder")) },
            text = {
                OutlinedTextField(
                    value = newEntityName,
                    onValueChange = { newEntityName = it },
                    placeholder = { Text(tr("Folder Name")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEntityName.isNotBlank()) {
                        currentFolder.items.add(FileSystemItem.Folder(id = System.currentTimeMillis().toString(), name = newEntityName))
                        newEntityName = ""
                        showCreateFolderDialog = false
                    }
                }) { Text(tr("Create")) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(tr("Cancel")) }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(tr("Create New Note")) },
            text = {
                OutlinedTextField(
                    value = newEntityName,
                    onValueChange = { newEntityName = it },
                    placeholder = { Text(tr("Note name")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEntityName.isNotBlank()) {
                        currentFolder.items.add(FileSystemItem.NoteFile(id = System.currentTimeMillis().toString(), name = newEntityName.trim()))
                        newEntityName = ""
                        showCreateFileDialog = false
                    }
                }) { Text(tr("Create")) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text(tr("Cancel")) }
            }
        )
    }
}
