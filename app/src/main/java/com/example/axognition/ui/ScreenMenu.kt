package com.example.axognition.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.axognition.ui.theme.AxognitionTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

private data class ScreenMenuItem(
    val id: Int,
    val routeTitle: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

// Route titles are kept separate from labels so existing navigation IDs stay stable.
private val screenMenuItems = listOf(
    ScreenMenuItem(1, "Lectures", "Lectures", "Watch and learn", Icons.Default.Book),
    ScreenMenuItem(2, "Homeworks", "Homework", "Keep up with your work", Icons.AutoMirrored.Filled.Assignment),
    ScreenMenuItem(3, "Practice", "Practice", "Write, draw and explore", Icons.Default.Edit),
    ScreenMenuItem(4, "Test", "Test", "See what you know", Icons.AutoMirrored.Filled.FactCheck),
    ScreenMenuItem(5, "Courses", "Courses", "Build your knowledge", Icons.AutoMirrored.Filled.LibraryBooks),
    ScreenMenuItem(6, "Books", "Books", "Find your next read", Icons.AutoMirrored.Filled.MenuBook),
    ScreenMenuItem(7, "Exersies", "Exercises", "Try something new", Icons.Default.FitnessCenter),
    ScreenMenuItem(8, "Games", "Games", "Learn through play", Icons.Default.SportsEsports),
    ScreenMenuItem(9, "Call-Messages", "Messages & Calls", "Stay connected", Icons.Default.ChatBubbleOutline),
    ScreenMenuItem(10, "Map", "Map", "Explore around you", Icons.Default.Explore)
)

@Composable
fun DashboardScreen(childName: String, modifier: Modifier = Modifier, onItemClick: (String) -> Unit) {
    var itemOrder by rememberSaveable { mutableStateOf(screenMenuItems.map { it.id }.toIntArray()) }
    val orderedItems = itemOrder.map { id -> screenMenuItems.first { it.id == id } }
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        itemOrder = itemOrder.toMutableList().apply { add(to.index, removeAt(from.index)) }.toIntArray()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Use the available window, including split screen, rather than physical device orientation.
        val compactHeight = maxHeight < 440.dp
        val landscape = maxWidth > maxHeight
        val horizontalPadding = if (maxWidth >= 600.dp) 24.dp else 16.dp
        val columns = when {
            maxWidth >= 1000.dp -> 5
            landscape && maxWidth >= 700.dp -> 5
            maxWidth >= 760.dp -> 4
            landscape && maxWidth >= 600.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        val cardHeight = if (compactHeight) 126.dp else 156.dp

        Column(Modifier.fillMaxSize().padding(horizontal = horizontalPadding)) {
            Row(
                Modifier.fillMaxWidth().padding(top = if (compactHeight) 8.dp else 18.dp, bottom = if (compactHeight) 12.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Welcome back, $childName"),
                        fontSize = if (compactHeight) 22.sp else 28.sp,
                        lineHeight = if (compactHeight) 28.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!compactHeight) {
                        Text(
                            tr("Choose something to get started."),
                            Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!landscape) ReorderHint(Modifier.padding(top = 10.dp))
                }
                if (landscape) ReorderHint()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.weight(1f).testTag("screen-menu-grid"),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orderedItems, key = { it.id }) { item ->
                    ReorderableItem(reorderState, key = item.id) { isDragging ->
                        val scale by animateFloatAsState(
                            if (isDragging) 1.025f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "menuCardScale"
                        )
                        ScreenMenuCard(
                            item = item,
                            compact = compactHeight,
                            dragging = isDragging,
                            modifier = Modifier.fillMaxWidth().height(cardHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                .longPressDraggableHandle().scale(scale),
                            onClick = { onItemClick(item.routeTitle) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderHint(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Default.DragIndicator, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr("Hold to rearrange"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenMenuCard(item: ScreenMenuItem, compact: Boolean, dragging: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val iconBackground: Color
    val iconColor: Color
    when (item.id % 3) {
        1 -> { iconBackground = scheme.primaryContainer; iconColor = scheme.primary }
        2 -> { iconBackground = scheme.secondaryContainer; iconColor = scheme.secondary }
        else -> { iconBackground = scheme.tertiary.copy(alpha = 0.14f); iconColor = scheme.tertiary }
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, if (dragging) scheme.primary else scheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 10.dp else 0.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = iconBackground) {
                    Box(Modifier.size(if (compact) 34.dp else 42.dp), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, Modifier.size(if (compact) 20.dp else 24.dp), tint = iconColor)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            Text(tr(item.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(tr(item.description), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class PanelMenuItem(val route: String, val title: String, val icon: ImageVector)
private val panelMenuItems = listOf(
    PanelMenuItem("panel_profile", "Profile", Icons.Default.PersonOutline),
    PanelMenuItem("panel_performance", "Performance", Icons.Default.Insights),
    PanelMenuItem("panel_health", "Health", Icons.Default.FavoriteBorder),
    PanelMenuItem("panel_calendar", "Calendar", Icons.Default.CalendarToday),
    PanelMenuItem("panel_tasks", "Today's Tasks", Icons.Default.TaskAlt),
    PanelMenuItem("panel_time", "Time", Icons.Default.Schedule),
    PanelMenuItem("panel_settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavigationMenu(childName: String, currentRoute: String?, darkMode: Boolean, onDarkModeChanged: (Boolean) -> Unit, onNavigate: (String) -> Unit, onClose: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("app-menu-drawer"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Axognition", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, tr("Close menu")) }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Text(childName.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(childName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(tr("Your learning space"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            Text(tr("Your day"), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.GridView, null) },
                label = { Text(tr("All screens")) },
                selected = currentRoute == "dashboard",
                shape = RoundedCornerShape(14.dp),
                onClick = { onNavigate("dashboard") }
            )
        }
        items(panelMenuItems.size) { index ->
            val item = panelMenuItems[index]
            if (item.route == "panel_settings") HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavigationDrawerItem(
                icon = { Icon(item.icon, null) },
                label = { Text(tr(item.title)) },
                selected = currentRoute == item.route,
                shape = RoundedCornerShape(14.dp),
                onClick = { onNavigate(item.route) }
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("Dark Mode"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = darkMode, onCheckedChange = onDarkModeChanged)
            }
        }
    }
}

@Preview(name = "Menu • Portrait • Light", widthDp = 390, heightDp = 760, showBackground = true)
@Composable
private fun MenuPortraitLightPreview() { AxognitionTheme(false) { DashboardScreen("Alex", onItemClick = {}) } }

@Preview(name = "Menu • Portrait • Dark", widthDp = 390, heightDp = 760, showBackground = true)
@Composable
private fun MenuPortraitDarkPreview() { AxognitionTheme(true) { DashboardScreen("Alex", onItemClick = {}) } }

@Preview(name = "Menu • Landscape • Light", widthDp = 840, heightDp = 360, showBackground = true)
@Composable
private fun MenuLandscapeLightPreview() { AxognitionTheme(false) { DashboardScreen("Alex", onItemClick = {}) } }

@Preview(name = "Menu • Landscape • Dark", widthDp = 840, heightDp = 360, showBackground = true)
@Composable
private fun MenuLandscapeDarkPreview() { AxognitionTheme(true) { DashboardScreen("Alex", onItemClick = {}) } }
