package com.example.axognition.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

private data class DayCell(
    val millis: Long?,
    val dayNumber: Int,
    val dayNumberStr: String
)

private data class MonthOverviewData(
    val monthIndex: Int,
    val monthName: String,
    val isCurrentMonth: Boolean,
    val days: List<DayCell?>
)

@Composable
fun CalendarPanelScreen(onBack: () -> Unit) {
    val calendarInstance = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(calendarInstance.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(calendarInstance.get(Calendar.MONTH)) }
    var isZoomedOut by remember { mutableStateOf(false) }

    val todayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    var selectedDateMillis by remember { mutableLongStateOf(todayMillis) }

    val gridDays = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(displayedYear, displayedMonth, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1

        val list = mutableListOf<DayCell?>()
        for (i in 0 until (firstDayOfWeek + maxDays)) {
            if (i < firstDayOfWeek) {
                list.add(null)
            } else {
                val dayNum = i - firstDayOfWeek + 1
                val dayCal = Calendar.getInstance().apply {
                    set(displayedYear, displayedMonth, dayNum, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                list.add(DayCell(millis = dayCal.timeInMillis, dayNumber = dayNum, dayNumberStr = dayNum.toString()))
            }
        }
        list
    }

    val yearOverviewData = remember(displayedYear) {
        val now = Calendar.getInstance()
        val currentY = now.get(Calendar.YEAR)
        val currentM = now.get(Calendar.MONTH)
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

        (0..11).map { monthIndex ->
            val cal = Calendar.getInstance().apply { set(displayedYear, monthIndex, 1) }
            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val monthName = monthFormat.format(cal.time)
            val isCurrent = (currentY == displayedYear && currentM == monthIndex)

            val daysList = mutableListOf<DayCell?>()
            for (i in 0 until (firstDayOfWeek + maxDays)) {
                if (i < firstDayOfWeek) {
                    daysList.add(null)
                } else {
                    val dayNum = i - firstDayOfWeek + 1
                    daysList.add(DayCell(millis = null, dayNumber = dayNum, dayNumberStr = dayNum.toString()))
                }
            }
            MonthOverviewData(monthIndex, monthName, isCurrent, daysList)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    text = if (isZoomedOut) "$displayedYear Overview" else "Calendar",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = { isZoomedOut = !isZoomedOut }) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Toggle Zoom View",
                    tint = if (isZoomedOut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val headerTitle = remember(displayedYear, displayedMonth, isZoomedOut) {
            if (isZoomedOut) {
                displayedYear.toString()
            } else {
                val cal = Calendar.getInstance().apply { set(displayedYear, displayedMonth, 1) }
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                IconButton(onClick = {
                    if (isZoomedOut) {
                        displayedYear--
                    } else {
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear--
                        } else {
                            displayedMonth--
                        }
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                IconButton(onClick = {
                    if (isZoomedOut) {
                        displayedYear++
                    } else {
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear++
                        } else {
                            displayedMonth++
                        }
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (!isZoomedOut) {
            val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                weekDays.forEach { day ->
                    Text(
                        text = day,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (!isZoomedOut) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = gridDays,
                    key = { cell -> cell?.millis ?: java.util.UUID.randomUUID().toString() }
                ) { cell ->
                    if (cell != null) {
                        val isSelected = cell.millis == selectedDateMillis
                        val isToday = cell.millis == todayMillis

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable { selectedDateMillis = cell.millis!! },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cell.dayNumberStr,
                                fontSize = 20.sp, // Increased font size for zoomed-in numbers
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.aspectRatio(1f))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = yearOverviewData,
                    key = { monthData -> monthData.monthIndex }
                ) { monthData ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clickable {
                                displayedMonth = monthData.monthIndex
                                isZoomedOut = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (monthData.isCurrentMonth) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = monthData.monthName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (monthData.isCurrentMonth) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                monthData.days.chunked(7).forEach { weekChunk ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        weekChunk.forEach { dayCell ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(
                                                        if (dayCell != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.04f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (dayCell != null) {
                                                    Text(
                                                        text = dayCell.dayNumberStr,
                                                        fontSize = 19.sp, // Increased font size for zoomed-out year grid numbers
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                        repeat(7 - weekChunk.size) {
                                            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val formattedSelectedDate = remember(selectedDateMillis) {
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Selected Date",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedSelectedDate,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}