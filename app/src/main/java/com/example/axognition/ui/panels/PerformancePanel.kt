package com.example.axognition.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data models for Grades structure covering lectures and courses
private data class GradeItem(
    val subject: String,
    val grade: String,
    val type: String, // "Lecture" or "Course"
    val scorePercentage: Int
)

private data class PeriodFolder(
    val periodName: String,
    val dateRange: String,
    val grades: List<GradeItem>
)

@Composable
fun PerformancePanelScreen(onBack: () -> Unit) {
    // Sorted grade folders with the latest period automatically on top
    val gradeFolders = remember {
        listOf(
            PeriodFolder(
                periodName = "Spring 2026 - Midterms",
                dateRange = "Mar 2026 - Apr 2026",
                grades = listOf(
                    GradeItem("Advanced Algorithms", "A", "Course", 94),
                    GradeItem("Database Systems Lecture", "A-", "Lecture", 90),
                    GradeItem("Software Engineering", "B+", "Course", 88),
                    GradeItem("Operating Systems Lecture", "A", "Lecture", 95)
                )
            ),
            PeriodFolder(
                periodName = "Winter 2026 - Finals",
                dateRange = "Jan 2026 - Feb 2026",
                grades = listOf(
                    GradeItem("Data Structures", "A", "Course", 96),
                    GradeItem("Computer Networks Lecture", "B", "Lecture", 85),
                    GradeItem("Discrete Math", "A-", "Course", 91),
                    GradeItem("Web Development Lecture", "A+", "Lecture", 98)
                )
            ),
            PeriodFolder(
                periodName = "Fall 2025 - Finals",
                dateRange = "Nov 2025 - Dec 2025",
                grades = listOf(
                    GradeItem("Linear Algebra", "B+", "Course", 87),
                    GradeItem("Physics Lecture", "B", "Lecture", 82),
                    GradeItem("Object Oriented Programming", "A", "Course", 93)
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Academic Performance",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Folders List View for Periods (Latest on Top)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gradeFolders) { folder ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Period Folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = folder.periodName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = folder.dateRange,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Individual subject item rows with the entire card background tinted based on type
                        for (item in folder.grades) {
                            val isLecture = item.type.equals("Lecture", ignoreCase = true)
                            val surfaceBgColor = if (isLecture) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val badgeContainerColor = if (isLecture) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                            val badgeContentColor = if (isLecture) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = surfaceBgColor,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.subject,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Type: ${item.type}",
                                            fontSize = 12.sp,
                                            color = if (isLecture) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${item.scorePercentage}%",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(badgeContainerColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.grade,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeContentColor
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
    }
}