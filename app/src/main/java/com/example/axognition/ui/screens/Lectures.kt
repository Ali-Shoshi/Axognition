package com.example.axognition.ui.screens

import com.example.axognition.ui.tr

import androidx.activity.compose.BackHandler
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.axognition.data.LectureProgressStore
import com.example.axognition.data.LectureSync
import com.example.axognition.data.LectureScore
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
    LaunchedEffect(Unit) { LectureSync.refreshCompletions(context) }
    var completedLectures by remember { mutableStateOf(progress.completedLectures()) }
    var scores by remember { mutableStateOf(progress.scores()) }
    DisposableEffect(progress) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            completedLectures = progress.completedLectures()
            scores = progress.scores()
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
                                    LectureScoreSummary(scores[lecture.id])
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
            val mathematics = subject.units.any { it.id == "fractions" || it.id == "geometry" }
            val doneUnits = subject.units.count { unitData ->
                unitData.lectures.isNotEmpty() && unitData.lectures.all { it.id in completedLectures }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (mathematics) {
                    Text(
                        text = tr("$doneUnits of ${subject.units.size} units complete"),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (subject.units.isEmpty()) 0f else doneUnits.toFloat() / subject.units.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFF16834F),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    MathematicsUnitList(
                        units = subject.units,
                        completedLectures = completedLectures,
                        scores = scores,
                        onUnit = { selectedUnit = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
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
                                modifier = Modifier.fillMaxWidth().clickable { selectedUnit = unit },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tr(unit.title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(tr(if (unit.lectures.size == 1) "1 video lecture" else "${unit.lectures.size} video lectures"), fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> LectureCollections(completedLectures = completedLectures, onSubject = { selectedSubject = it })
    }
}

@Composable
private fun MathematicsUnitList(
    units: List<UnitData>,
    completedLectures: Set<String>,
    scores: Map<String, LectureScore>,
    onUnit: (UnitData) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(units, key = { _, unit -> unit.id }) { index, unit ->
            val done = unit.lectures.isNotEmpty() && unit.lectures.all { it.id in completedLectures }
            val score = unit.lectures.firstNotNullOfOrNull { scores[it.id] }
            MathematicsUnitCard(unit, index + 1, done, score, onUnit)
        }
    }
}

@Composable
private fun MathematicsUnitCard(
    unit: UnitData,
    number: Int,
    done: Boolean,
    score: LectureScore?,
    onUnit: (UnitData) -> Unit
) {
    val onCard = if (done) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = onCard.copy(alpha = if (done) .84f else .72f)
    val accent = if (done) Color.White else MaterialTheme.colorScheme.primary
    val background = if (done) Brush.linearGradient(listOf(Color(0xFF16834F), Color(0xFF0D6640)))
        else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
    Card(
        onClick = { onUnit(unit) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(background)) {
            if (maxWidth >= 560.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    MathematicsUnitIntro(unit, number, onCard, secondary, Modifier.weight(1f))
                    MathematicsUnitResult(done, score, onCard, secondary, accent, Modifier.weight(.85f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = onCard)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        MathematicsUnitIntro(unit, number, onCard, secondary, Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = onCard)
                    }
                    Spacer(Modifier.height(12.dp))
                    MathematicsUnitResult(done, score, onCard, secondary, accent, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MathematicsUnitIntro(
    unit: UnitData,
    number: Int,
    onCard: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Surface(shape = CircleShape, color = onCard.copy(alpha = .12f)) {
            Text(tr("Unit $number"), Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
                color = onCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(7.dp))
        Text(tr(unit.title), color = onCard, fontSize = 20.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(tr(if (unit.lectures.size == 1) "1 video lecture" else "${unit.lectures.size} video lectures"),
            color = secondary, fontSize = 13.sp)
    }
}

@Composable
private fun MathematicsUnitResult(
    done: Boolean,
    score: LectureScore?,
    onCard: Color,
    secondary: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = when {
                score != null -> "${score.percent}%"
                done -> tr("Finished ✓")
                else -> tr("Ready to begin")
            },
            color = onCard,
            fontSize = if (score != null) 32.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (score != null) 36.sp else 26.sp
        )
        if (score != null) {
            Text(tr(if (done) "Finished ✓" else "Not passed yet"), color = secondary,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { (score?.percent ?: if (done) 100 else 0) / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = accent,
            trackColor = onCard.copy(alpha = .16f)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                score != null -> tr("${score.percent}% · ${score.correct}/${score.total} correct · ${score.attempts} completed attempts")
                done -> tr("Score available after the next attempt")
                else -> tr("No completed attempts yet.")
            },
            color = secondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun LectureScoreSummary(score: LectureScore?) {
    if (score == null) {
        Text(tr("No completed attempts yet."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(tr("${score.percent}% · ${score.correct}/${score.total} correct · ${score.attempts} completed attempts"),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!score.passed) Text(tr("Not passed yet"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}
