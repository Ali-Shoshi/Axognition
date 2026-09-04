package com.example.axognition.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.axognition.ui.KioskTopBar

private enum class DailyTaskKind(val icon: ImageVector, val tint: Color) {
    LECTURE(Icons.Default.PlayCircle, Color(0xFF2563EB)),
    HOMEWORK(Icons.Default.Assignment, Color(0xFF9333EA)),
    REVIEW(Icons.Default.Quiz, Color(0xFFEA580C))
}

private data class DailyTask(
    val id: String,
    val title: String,
    val detail: String,
    val time: String,
    val kind: DailyTaskKind,
    val duration: String
)

private val todayTasks = listOf(
    DailyTask("math-lecture", "Watch: The Power Rule", "Mathematics · Unit 2: Derivatives", "09:00", DailyTaskKind.LECTURE, "14 min"),
    DailyTask("physics-homework", "Finish kinematics worksheet", "Physics · Questions 1–12", "Due 14:00", DailyTaskKind.HOMEWORK, "35 min"),
    DailyTask("daily-review", "Daily review test", "A short quiz from recent lessons", "Any time", DailyTaskKind.REVIEW, "10 questions"),
    DailyTask("chemistry-lecture", "Watch: Periodic Trends", "Chemistry · Unit 1", "16:30", DailyTaskKind.LECTURE, "14 min"),
    DailyTask("math-practice", "Practice derivative rules", "Mathematics · Mixed exercises", "Before 20:00", DailyTaskKind.HOMEWORK, "20 min")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksPanelScreen(onBack: () -> Unit) {
    var completedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val completion = completedIds.size.toFloat() / todayTasks.size
    val remaining = todayTasks.size - completedIds.size

    Scaffold(
        topBar = { KioskTopBar(title = "Today's tasks", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("TODAY'S PLAN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (remaining == 0) "Everything is complete!" else "$remaining task${if (remaining == 1) "" else "s"} left",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${completedIds.size} of ${todayTasks.size} completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            item {
                Text("Your agenda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            }

            items(todayTasks, key = { it.id }) { task ->
                DailyTaskCard(
                    task = task,
                    complete = task.id in completedIds,
                    onToggle = {
                        completedIds = if (task.id in completedIds) completedIds - task.id else completedIds + task.id
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyTaskCard(task: DailyTask, complete: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (complete) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(task.kind.tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(task.kind.icon, contentDescription = null, tint = task.kind.tint)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(task.time.uppercase(), style = MaterialTheme.typography.labelSmall, color = task.kind.tint, fontWeight = FontWeight.Bold)
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(task.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text(task.duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (complete) "Mark incomplete" else "Mark complete",
                tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
