package com.example.axognition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.axognition.data.RemoteSubject
import com.example.axognition.data.SubjectApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LectureCollections(onSubject: (Subject) -> Unit) {
    // Temporary grade until the signed-in student's profile supplies it.
    val grade = 1
    var subjects by remember { mutableStateOf<List<RemoteSubject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var previousYear by remember { mutableStateOf(false) }
    LaunchedEffect(grade, retry) {
        loading = true
        error = null
        try {
            subjects = withContext(Dispatchers.IO) { SubjectApi.fetchSubjects(grade) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not reach the server"
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your lectures", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Grade $grade · A little progress, every day", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { retry++ }) { Icon(Icons.Default.Refresh, "Refresh subjects") }
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!)
                Button(onClick = { retry++ }) { Text("Try again") }
            }
            subjects.isEmpty() -> Text("No subjects available for Grade $grade yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(subjects, key = { _, subject -> subject.id }) { index, subject ->
                    // Preview progress only: no invented completion records are saved to the database.
                    val total = 8 + index % 5
                    val done = (index * 3 + 2) % total
                    LectureSubjectCard(subject, index, done, total) {
                        onSubject(Subject(subject.id, subject.name, "", List(total) { unit ->
                            UnitData("${subject.id}-preview-$unit", "Unit ${unit + 1} · Preview", emptyList())
                        }))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedCard(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clickable { previousYear = true },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null)
                            Text("Previous year's lectures", Modifier.weight(1f).padding(horizontal = 14.dp), fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }
    if (previousYear) AlertDialog(
        onDismissRequest = { previousYear = false },
        title = { Text("Previous year's lectures") },
        text = { Text("Your earlier lectures will be available here soon.") },
        confirmButton = { TextButton(onClick = { previousYear = false }) { Text("Got it") } }
    )
}

@Composable
private fun LectureSubjectCard(subject: RemoteSubject, index: Int, done: Int, total: Int, onClick: () -> Unit) {
    val colors = listOf(0xFFB83969, 0xFF98611D, 0xFF226CA1, 0xFF487D35, 0xFFB64C36, 0xFF884B91, 0xFF326B69, 0xFF525A93)
    val accent = Color(colors[index % colors.size])
    val icon = when {
        subject.name.contains("Language") -> Icons.Default.Translate
        subject.name.contains("Mathematics") -> Icons.Default.Calculate
        subject.name.contains("Nature") -> Icons.Default.Eco
        subject.name.contains("Society") -> Icons.Default.Groups
        subject.name.contains("Arts") -> Icons.Default.Palette
        subject.name.contains("Music") -> Icons.Default.MusicNote
        subject.name.contains("Physical") -> Icons.Default.SportsSoccer
        else -> Icons.Default.AutoAwesome
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = .65f), accent), radius = 650f))) {
            Icon(icon, null, Modifier.align(Alignment.TopEnd).padding(12.dp).size(64.dp), tint = Color.White.copy(alpha = .13f))
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Icon(icon, null, Modifier.size(26.dp), tint = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(subject.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp)
                Text("$done / $total units done", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Color.White, trackColor = Color.White.copy(alpha = .22f))
            }
        }
    }
}
