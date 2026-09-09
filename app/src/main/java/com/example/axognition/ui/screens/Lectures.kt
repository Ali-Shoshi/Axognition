package com.example.axognition.ui.screens

import com.example.axognition.ui.tr

import androidx.activity.compose.BackHandler
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.axognition.data.LectureProgressStore
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Dummy Data Models ---
data class VideoLecture(
    val id: String,
    val title: String,
    val duration: String
)

data class UnitData(
    val id: String,
    val title: String,
    val lectures: List<VideoLecture>
)

data class Subject(
    val id: String,
    val name: String,
    val description: String,
    val units: List<UnitData>
)


@Composable
fun LecturesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val progress = remember(context) { LectureProgressStore(context) }
    var completedLectures by remember { mutableStateOf(progress.completedLectures()) }
    DisposableEffect(progress) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            completedLectures = progress.completedLectures()
        }
        progress.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { progress.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var selectedUnit by remember { mutableStateOf<UnitData?>(null) }
    var playingVideo by remember { mutableStateOf<VideoLecture?>(null) }

    // Keep back navigation inside the lecture flow before allowing NavController to leave it.
    BackHandler(enabled = playingVideo != null || selectedUnit != null || selectedSubject != null) {
        when {
            playingVideo != null -> playingVideo = null
            selectedUnit != null -> selectedUnit = null
            selectedSubject != null -> selectedSubject = null
            else -> onBack()
        }
    }

    // Lazy list callbacks can run while the previous screen is being disposed.
    // Capture this composition's values so Back cannot invalidate their data.
    val subject = selectedSubject
    val unit = selectedUnit
    val video = playingVideo
    when {
        video?.id == "geometry" -> GeometryLesson(onBack = { playingVideo = null })
        video?.id == "fractions" -> FractionsLesson(onBack = { playingVideo = null })
        video != null -> {
            // Video Player Simulation Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = tr(video.title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = tr("Duration: ${video.duration}"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { playingVideo = null }) {
                        Text(tr("Close Video"))
                    }
                }
            }
        }
        unit != null -> {
            // Unit Detail: List of Lectures
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedUnit = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back to Units"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr(unit.title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = tr("${unit.lectures.size} lectures available"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(unit.lectures, key = { it.id }) { lecture ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playingVideo = lecture },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = tr(lecture.title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = tr(lecture.duration), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (lecture.id in completedLectures) {
                                        Text(tr("Finished ✓"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        subject != null -> {
            // Subject Detail: List of Units
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedSubject = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back to Subjects"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr(subject.name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = tr("Select a unit to view lectures:"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(subject.units, key = { it.id }) { unit ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedUnit = unit },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = tr(unit.title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tr("${unit.lectures.size} video lectures"),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (unit.lectures.isNotEmpty() && unit.lectures.all { it.id in completedLectures }) {
                                        Text(tr("Finished ✓"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> LectureCollections(completedLectures = completedLectures, onSubject = { selectedSubject = it })
    }
}
