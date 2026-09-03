package com.example.axognition.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Data Models ---
data class ExerciseDetail(
    val name: String,
    val description: String,
    val tips: String
)

data class MuscleGroup(
    val name: String,
    val exercise: ExerciseDetail
)

data class SportCategory(
    val id: String,
    val name: String,
    val description: String,
    val muscleGroups: List<MuscleGroup>
)

data class CardioType(
    val id: String,
    val name: String,
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val videoTitle: String,
    val duration: String
)

// --- Dummy Content ---
val dummySports = listOf(
    SportCategory(
        id = "weightlifting",
        name = "Strength & Weightlifting",
        description = "Resistance training focused on hypertrophy and muscle development.",
        muscleGroups = listOf(
            MuscleGroup(
                name = "Chest",
                exercise = ExerciseDetail(
                    name = "Barbell Bench Press",
                    description = "A compound movement targeting the pectoralis major, anterior deltoids, and triceps.",
                    tips = "Keep your feet flat on the floor, retract your shoulder blades, and lower the bar smoothly to your mid-chest."
                )
            ),
            MuscleGroup(
                name = "Back",
                exercise = ExerciseDetail(
                    name = "Lat Pulldown",
                    description = "An effective exercise for building width in the latissimus dorsi muscles.",
                    tips = "Lean back slightly, pull with your elbows rather than your hands, and squeeze your shoulder blades at the bottom."
                )
            ),
            MuscleGroup(
                name = "Legs",
                exercise = ExerciseDetail(
                    name = "Barbell Back Squat",
                    description = "The king of lower body exercises, working quadriceps, hamstrings, and glutes.",
                    tips = "Maintain a neutral spine, push your knees outward tracking over your toes, and break parallel if mobility allows."
                )
            )
        )
    ),
    SportCategory(
        id = "calisthenics",
        name = "Calisthenics & Bodyweight",
        description = "Mastering body weight through dynamic and static movements.",
        muscleGroups = listOf(
            MuscleGroup(
                name = "Shoulders & Arms",
                exercise = ExerciseDetail(
                    name = "Pike Push-up",
                    description = "A bodyweight movement that shifts focus to the shoulders and triceps.",
                    tips = "Elevate your hips high to form an inverted V shape, then lower your head forward toward the floor."
                )
            ),
            MuscleGroup(
                name = "Core",
                exercise = ExerciseDetail(
                    name = "Hanging Leg Raise",
                    description = "Advanced core builder targeting the lower rectus abdominis and hip flexors.",
                    tips = "Avoid swinging by engaging your lats and keeping your body completely rigid."
                )
            )
        )
    )
)

val dummyCardioTypes = listOf(
    CardioType(
        id = "hiit",
        name = "High-Intensity Interval Training (HIIT)",
        description = "Short bursts of intense exercise alternated with low-intensity recovery periods.",
        pros = listOf("Burns high calories quickly", "Improves cardiovascular endurance", "Requires minimal time"),
        cons = listOf("High impact on joints", "Risk of burnout or injury if overdone"),
        videoTitle = "20-Minute Full Body HIIT Session",
        duration = "20 min"
    ),
    CardioType(
        id = "liss",
        name = "Low-Intensity Steady-State (LISS)",
        description = "Sustained, low-intensity aerobic training like brisk walking or cycling.",
        pros = listOf("Very easy to recover from", "Low risk of injury", "Great for fat burning and active recovery"),
        cons = listOf("Takes significantly more time", "Slower caloric burn rate per session"),
        videoTitle = "45-Minute Fat-Burning Incline Walk",
        duration = "45 min"
    ),
    CardioType(
        id = "running",
        name = "Endurance Running",
        description = "Long-distance continuous running to build aerobic capacity.",
        pros = listOf("Strengthens heart and lung capacity", "Builds mental resilience", "Builds strong lower body bones"),
        cons = listOf("High repetitive impact on knees and ankles", "Requires good baseline conditioning"),
        videoTitle = "Proper Running Form & Pace Guidance",
        duration = "30 min"
    )
)

@Composable
fun ExercisesScreen(onBack: () -> Unit) {
    var currentTab by remember { mutableStateOf(0) } // 0: Sports/Muscles, 1: Cardio
    var selectedSport by remember { mutableStateOf<SportCategory?>(null) }
    var selectedMuscle by remember { mutableStateOf<MuscleGroup?>(null) }
    var selectedCardio by remember { mutableStateOf<CardioType?>(null) }
    var playingCardioVideo by remember { mutableStateOf<CardioType?>(null) }

    BackHandler {
        when {
            playingCardioVideo != null -> playingCardioVideo = null
            selectedCardio != null -> selectedCardio = null
            selectedMuscle != null -> selectedMuscle = null
            selectedSport != null -> selectedSport = null
            else -> onBack()
        }
    }

    when {
        playingCardioVideo != null -> {
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
                    Text(text = playingCardioVideo!!.videoTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Duration: ${playingCardioVideo!!.duration}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { playingCardioVideo = null }) {
                        Text("Close Video")
                    }
                }
            }
        }
        selectedMuscle != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedMuscle = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Muscles")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedMuscle!!.exercise.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "Overview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = selectedMuscle!!.exercise.description, fontSize = 14.sp)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Form & Technique Tips", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = selectedMuscle!!.exercise.tips, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        selectedSport != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedSport = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Sports")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedSport!!.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = selectedSport!!.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Select Target Muscle Group:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(selectedSport!!.muscleGroups) { muscle ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMuscle = muscle },
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
                                    Text(text = muscle.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Featured: ${muscle.exercise.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        selectedCardio != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedCardio = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Cardio List")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedCardio!!.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = selectedCardio!!.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "Pros", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                selectedCardio!!.pros.forEach { pro ->
                                    Text(text = "• $pro", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Cons", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                selectedCardio!!.cons.forEach { con ->
                                    Text(text = "• $con", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { playingCardioVideo = selectedCardio },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Watch Guidance Video")
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
                    text = "Sports & Fitness",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Explore strength training and cardio routines",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Sports & Muscles") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Cardio Library") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (currentTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(dummySports) { sport ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSport = sport },
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
                                            Text(text = sport.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = sport.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(dummyCardioTypes) { cardio ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCardio = cardio },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(20.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = cardio.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = cardio.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(imageVector = Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
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
