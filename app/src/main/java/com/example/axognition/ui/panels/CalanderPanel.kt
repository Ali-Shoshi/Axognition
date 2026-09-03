package com.example.axognition.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.axognition.ui.KioskTopBar
import java.text.SimpleDateFormat
import java.util.*

private data class SchoolEvent(val id: String, val title: String, val time: String, val date: Long, val kind: EventKind)
private enum class EventKind(val label: String, val color: Color) { CLASS("Class", Color(0xFF2764A5)), DEADLINE("Deadline", Color(0xFFBE2635)), EVENT("School event", Color(0xFF138A50)), REMINDER("My reminder", Color(0xFF8B4BB0)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPanelScreen(onBack: () -> Unit) {
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }; var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }; var selectedDay by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }
    val events = remember { mutableStateListOf(
        SchoolEvent("class", "Mathematics", "08:00 – 09:00", dayMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)), EventKind.CLASS),
        SchoolEvent("deadline", "Science project due", "Before 16:00", dayMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH) + 2), EventKind.DEADLINE),
        SchoolEvent("event", "Debate club meeting", "15:30 – 16:30", dayMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH) + 4), EventKind.EVENT)) }
    var showAddDialog by remember { mutableStateOf(false) }
    val selectedMillis = dayMillis(year, month, selectedDay)
    val monthTitle = remember(year, month) { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(dayMillis(year, month, 1))) }
    val days = remember(year, month) { monthCells(year, month) }
    val selectedEvents = events.filter { it.date == selectedMillis }
    val upcoming = events.filter { it.date >= selectedMillis }.sortedBy { it.date }.take(4)
    Scaffold(topBar = { KioskTopBar(title = "School calendar", onBack = onBack) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = { showAddDialog = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add reminder") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (month == 0) { month = 11; year-- } else month-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }; Text(monthTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); IconButton(onClick = { if (month == 11) { month = 0; year++ } else month++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") } } }
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7), userScrollEnabled = false,
                            modifier = Modifier.height(276.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(days, key = { it ?: -1 }) { day ->
                                if (day == null) {
                                    Spacer(Modifier.aspectRatio(1f))
                                } else {
                                    val date = dayMillis(year, month, day)
                                    val isSelected = date == selectedMillis
                                    val hasEvents = events.any { it.date == date }
                                    Column(
                                        Modifier.aspectRatio(1f).clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { selectedDay = day },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.toString(), color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (hasEvents) Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(selectedMillis)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (selectedEvents.isEmpty()) item { EmptyDayCard() } else items(selectedEvents, key = { it.id }) { EventCard(it) }
            item { Text("Coming up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(upcoming, key = { "upcoming-${it.id}" }) { EventCard(it, showDate = true) }
        }
    }
    if (showAddDialog) AddReminderDialog(selectedMillis, { showAddDialog = false }) { title, time -> events.add(SchoolEvent(UUID.randomUUID().toString(), title, time.ifBlank { "All day" }, selectedMillis, EventKind.REMINDER)); showAddDialog = false }
}

@Composable private fun EventCard(event: SchoolEvent, showDate: Boolean = false) = Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(event.kind.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Event, null, tint = event.kind.color) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (showDate) "${SimpleDateFormat("EEE, d MMM").format(Date(event.date))} · ${event.time}" else event.time, style = MaterialTheme.typography.bodySmall) }; Text(event.kind.label, style = MaterialTheme.typography.labelSmall, color = event.kind.color) } }
@Composable private fun EmptyDayCard() = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("Nothing scheduled yet. Add a personal reminder for this day.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun AddReminderDialog(date: Long, onDismiss: () -> Unit, onSave: (String, String) -> Unit) { var title by remember { mutableStateOf("") }; var time by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a reminder") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(date))); OutlinedTextField(title, { title = it }, label = { Text("What do you need to remember?") }, singleLine = true); OutlinedTextField(time, { time = it }, label = { Text("Time (optional)") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(title.trim(), time.trim()) }, enabled = title.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
private fun dayMillis(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun monthCells(year: Int, month: Int): List<Int?> { val c = Calendar.getInstance().apply { set(year, month, 1) }; return List(c.get(Calendar.DAY_OF_WEEK) - 1) { null } + (1..c.getActualMaximum(Calendar.DAY_OF_MONTH)).toList() }
