package com.example.axognition.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

val dummySubjects = listOf(
    Subject(
        id = "math",
        name = "Mathematics",
        description = "Calculus, Algebra, and Geometry",
        units = listOf(
            UnitData("m_u1", "Unit 1: Limits & Continuity", listOf(
                VideoLecture("v1", "Introduction to Limits", "12 min"),
                VideoLecture("v2", "Calculating Limits Algebraically", "18 min"),
                VideoLecture("v3", "Continuity at a Point", "15 min")
            )),
            UnitData("m_u2", "Unit 2: Derivatives", listOf(
                VideoLecture("v4", "The Power Rule", "14 min"),
                VideoLecture("v5", "Product and Quotient Rules", "22 min")
            ))
        )
    ),
    Subject(
        id = "physics",
        name = "Physics",
        description = "Mechanics, Thermodynamics, and Waves",
        units = listOf(
            UnitData("p_u1", "Unit 1: Kinematics", listOf(
                VideoLecture("v6", "Displacement and Velocity", "10 min"),
                VideoLecture("v7", "Acceleration Vectors", "16 min")
            ))
        )
    ),
    Subject(
        id = "chemistry",
        name = "Chemistry",
        description = "Atomic Structure and Bonding",
        units = listOf(
            UnitData("c_u1", "Unit 1: Periodic Table", listOf(
                VideoLecture("v8", "Periodic Trends", "14 min"),
                VideoLecture("v9", "Electron Configurations", "20 min")
            ))
        )
    )
)

@Composable
fun LecturesScreen(onBack: () -> Unit) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var selectedUnit by remember { mutableStateOf<UnitData?>(null) }
    var playingVideo by remember { mutableStateOf<VideoLecture?>(null) }

    when {
        playingVideo != null -> {
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
                    Text(text = playingVideo!!.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Duration: ${playingVideo!!.duration}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { playingVideo = null }) {
                        Text("Close Video")
                    }
                }
            }
        }
        selectedUnit != null -> {
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Units")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedUnit!!.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${selectedUnit!!.lectures.size} lectures available",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedUnit!!.lectures) { lecture ->
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
                                    Text(text = lecture.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = lecture.duration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        selectedSubject != null -> {
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Subjects")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedSubject!!.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select a unit to view lectures:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(selectedSubject!!.units) { unit ->
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
                                    Text(text = unit.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${unit.lectures.size} video lectures",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
        else -> {
            // Main Screen: Select Subject
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Video Lectures",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose a subject to get started",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dummySubjects) { subject ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSubject = subject },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subject.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subject.description,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${subject.units.size} Units",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Dashboard")
                }
            }
        }
    }
}