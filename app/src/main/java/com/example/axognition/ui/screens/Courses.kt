package com.example.axognition.ui.screens

import androidx.activity.compose.BackHandler
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

// --- Data Models (Reusing structural pattern from lectures) ---
data class CourseVideo(
    val id: String,
    val title: String,
    val duration: String
)

data class CourseUnit(
    val id: String,
    val title: String,
    val lectures: List<CourseVideo>
)

data class Course(
    val id: String,
    val title: String,
    val description: String,
    var isEnrolled: Boolean,
    val units: List<CourseUnit>
)

// --- Dummy Data ---
val initialCourses = mutableListOf(
    Course(
        id = "c1",
        title = "Advanced Mobile Development",
        description = "Master modern Jetpack Compose and architecture patterns.",
        isEnrolled = true,
        units = listOf(
            CourseUnit("u1", "Unit 1: State Management", listOf(
                CourseVideo("v1", "Understanding Recompositions", "15 min"),
                CourseVideo("v2", "State Hoisting Best Practices", "20 min")
            )),
            CourseUnit("u2", "Unit 2: Navigation & Architecture", listOf(
                CourseVideo("v3", "Compose Navigation Deep Dive", "25 min")
            ))
        )
    ),
    Course(
        id = "c2",
        title = "UI/UX Design Masterclass",
        description = "Learn user research, wireframing, and high-fidelity prototyping.",
        isEnrolled = true,
        units = listOf(
            CourseUnit("u3", "Unit 1: Design Fundamentals", listOf(
                CourseVideo("v4", "Color Theory & Typography", "18 min")
            ))
        )
    ),
    Course(
        id = "c3",
        title = "Data Science Foundations",
        description = "Introduction to Python, Pandas, and basic statistical analysis.",
        isEnrolled = false,
        units = listOf(
            CourseUnit("u4", "Unit 1: Python Basics", listOf(
                CourseVideo("v5", "Variables and Loops", "12 min"),
                CourseVideo("v6", "Working with Lists and Dictionaries", "22 min")
            ))
        )
    ),
    Course(
        id = "c4",
        title = "Artificial Intelligence Basics",
        description = "Explore machine learning concepts and neural networks.",
        isEnrolled = false,
        units = listOf(
            CourseUnit("u5", "Unit 1: Intro to Machine Learning", listOf(
                CourseVideo("v7", "Supervised vs Unsupervised Learning", "20 min")
            ))
        )
    )
)

@Composable
fun CoursesScreen(onBack: () -> Unit) {
    var coursesList = remember { mutableStateListOf<Course>().apply { addAll(initialCourses) } }
    var currentTab by remember { mutableStateOf(0) } // 0: Enrolled, 1: Available to Enroll
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var selectedUnit by remember { mutableStateOf<CourseUnit?>(null) }
    var playingVideo by remember { mutableStateOf<CourseVideo?>(null) }

    // Intercept back actions safely across navigation hierarchies
    BackHandler {
        when {
            playingVideo != null -> playingVideo = null
            selectedUnit != null -> selectedUnit = null
            selectedCourse != null -> selectedCourse = null
            else -> onBack()
        }
    }

    when {
        playingVideo != null -> {
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
        selectedCourse != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedCourse = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Courses")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedCourse!!.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = selectedCourse!!.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!selectedCourse!!.isEnrolled) {
                    Button(
                        onClick = {
                            val index = coursesList.indexOfFirst { it.id == selectedCourse!!.id }
                            if (index != -1) {
                                coursesList[index] = coursesList[index].copy(isEnrolled = true)
                                selectedCourse = coursesList[index]
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enroll in Course")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Course Units:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(selectedCourse!!.units) { unit ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedCourse!!.isEnrolled) {
                                        selectedUnit = unit
                                    }
                                },
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
                                    tint = if (selectedCourse!!.isEnrolled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Platform Courses",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manage your learning path",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Enrolled") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Available") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val displayList = coursesList.filter { if (currentTab == 0) it.isEnrolled else !it.isEnrolled }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayList) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCourse = course },
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
                                        text = course.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = course.description,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${course.units.size} Units",
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