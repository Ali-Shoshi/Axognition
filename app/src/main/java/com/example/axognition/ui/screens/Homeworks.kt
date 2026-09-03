package com.example.axognition.ui.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Data Models ---
enum class HomeworkCategory(val title: String, val icon: ImageVector, val description: String) {
    LECTURES("Lectures", Icons.Default.School, "Theory assignments & problem sets"),
    COURSES("Courses", Icons.Default.Code, "Practical labs & full-stack projects")
}

data class HomeworkItem(
    val id: String,
    val title: String,
    val category: HomeworkCategory,
    val dueDate: String,
    val grade: String?, // null if not graded/completed yet
    var isCompleted: Boolean
)

@Composable
fun HomeworksScreen(onBack: () -> Unit) {
    // Simulated homework list with dummy data
    val homeworkList = remember {
        mutableStateListOf(
            HomeworkItem("1", "8051 Assembly Timer Interrupt Lab", HomeworkCategory.LECTURES, "Due Tomorrow", "9.5", true),
            HomeworkItem("2", "Data Structures Complexity Analysis", HomeworkCategory.LECTURES, "Due Next Week", null, false),
            HomeworkItem("3", "Jetpack Compose UI Layouts & State", HomeworkCategory.COURSES, "Due in 3 days", "10.0", true),
            HomeworkItem("4", "Node.js REST API & Prisma Schema", HomeworkCategory.COURSES, "Due Next Week", null, false)
        )
    }

    // Navigation state: null means hub view, non-null means inside that specific category
    var selectedCategory by remember { mutableStateOf<HomeworkCategory?>(null) }
    BackHandler {
        if (selectedCategory != null) selectedCategory = null else onBack()
    }

    // Computed statistics for the top summary bar
    val totalTasks = homeworkList.size
    val completedTasks = homeworkList.count { it.isCompleted }
    val tasksLeft = totalTasks - completedTasks

    // Calculate dummy average grade from graded items
    val gradedItems = homeworkList.mapNotNull { it.grade?.toFloatOrNull() }
    val avgGrade = if (gradedItems.isNotEmpty()) String.format("%.1f", gradedItems.average()) else "N/A"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- COMPACT CUSTOM HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory?.title ?: "Homework Hub",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- TOP SUMMARY BAR ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryStatItem(label = "Left", value = "$tasksLeft", color = MaterialTheme.colorScheme.error)
                    VerticalDivider(modifier = Modifier.height(28.dp))
                    SummaryStatItem(label = "Done", value = "$completedTasks/$totalTasks", color = MaterialTheme.colorScheme.primary)
                    VerticalDivider(modifier = Modifier.height(28.dp))
                    SummaryStatItem(label = "Avg Grade", value = avgGrade, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedCategory == null) {
                // --- CATEGORY SELECTION HUB (2 Options) ---
                Text(
                    text = "Select Category",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (category in HomeworkCategory.entries) {
                        val count = homeworkList.count { it.category == category }
                        val pendingCount = homeworkList.count { it.category == category && !it.isCompleted }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { selectedCategory = category },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(3.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        category.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = category.description,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$pendingCount pending tasks ($count total)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

            } else {
                // --- HOMEWORK LIST VIEW FOR SELECTED CATEGORY ---
                val displayedHomeworks = homeworkList.filter { it.category == selectedCategory }

                Text(
                    text = "Assignments & Labs",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (displayedHomeworks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No homework assignments found here.",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedHomeworks, key = { it.id }) { homework ->
                            HomeworkCard(
                                homework = homework,
                                onToggleComplete = {
                                    val index = homeworkList.indexOfFirst { it.id == homework.id }
                                    if (index != -1) {
                                        val updated = homeworkList[index]
                                        homeworkList[index] = updated.copy(
                                            isCompleted = !updated.isCompleted,
                                            grade = if (!updated.isCompleted && updated.grade == null) "9.0" else updated.grade
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun HomeworkCard(homework: HomeworkItem, onToggleComplete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = homework.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = homework.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = homework.dueDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Grade / Status Badge
            if (homework.grade != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Grade: ${homework.grade}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Pending",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
