package com.example.axognition.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DoctorVisit(val date: String, val doctor: String, val notes: String, val isFuture: Boolean)
data class Allergy(val allergen: String, val reaction: String, val severity: String)
data class HealthProblem(val name: String, val status: String, val history: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPanelScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    val visits = listOf(
        DoctorVisit("12 Oct 2025", "Dr. Smith (General)", "Annual check-up, vitals normal.", false),
        DoctorVisit("15 Jan 2026", "Dr. Davis (Ortho)", "Evaluated lower back strain.", false),
        DoctorVisit("15 Jul 2026", "Dr. Smith (General)", "Routine bi-annual follow-up check.", true)
    )

    val allergies = listOf(
        Allergy("Penicillin", "Hives, Mild Rash", "Moderate"),
        Allergy("Peanuts", "Anaphylactic risk", "Severe"),
        Allergy("Dust Mites", "Sneezing, Nasal Congestion", "Mild")
    )

    val problems = listOf(
        HealthProblem("Flat Feet (Pes Planus)", "Stable", listOf(
            "2023: Diagnosed with arch pain after running",
            "2024: Custom orthotics fitted; pain decreased",
            "2025: Stable with regular orthotic use"
        )),
        HealthProblem("Lower Back Problem", "Worsening", listOf(
            "2024: Occasional stiffness after heavy lifting",
            "2025: Increased frequency of spasms during long sitting periods",
            "2026: Referred to physical therapy for chronic flare-ups"
        )),
        HealthProblem("Left Shoulder Muscle Strain", "Improving", listOf(
            "2025: Strain sustained during gym workout",
            "2026: Mobility mostly restored, light upper body training resumed"
        ))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Health", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            edgePadding = 0.dp,
                            modifier = Modifier.weight(1f),
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Visits", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Allergies", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Problems", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Doctor Visits (Past & Future)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        items(visits) { visit ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (visit.isFuture)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = visit.doctor, fontWeight = FontWeight.Bold)
                                        Text(text = visit.date, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = visit.notes, style = MaterialTheme.typography.bodySmall)
                                    if (visit.isFuture) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Upcoming Appointment",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Allergies List", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        items(allergies) { allergy ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = allergy.allergen, fontWeight = FontWeight.Bold)
                                        Text(text = "Reaction: ${allergy.reaction}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Badge {
                                        Text(allergy.severity)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Overall Problems & History", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        items(problems) { problem ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = problem.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = problem.status,
                                            color = when (problem.status) {
                                                "Improving" -> MaterialTheme.colorScheme.primary
                                                "Worsening" -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.secondary
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("History Timeline:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    problem.history.forEach { hist ->
                                        Text(
                                            text = "• $hist",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}