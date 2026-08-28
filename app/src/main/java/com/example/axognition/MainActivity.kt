package com.example.axognition

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.axognition.ui.theme.AxognitionTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

// Dashboard feature screens (remains in ui.screens)
import com.example.axognition.ui.screens.BooksScreen
import com.example.axognition.ui.screens.CallMessagesScreen
import com.example.axognition.ui.screens.CoursesScreen
import com.example.axognition.ui.screens.ExercisesScreen
import com.example.axognition.ui.screens.GamesScreen
import com.example.axognition.ui.screens.HomeworksScreen
import com.example.axognition.ui.screens.LecturesScreen
import com.example.axognition.ui.screens.MapScreen
import com.example.axognition.ui.screens.PracticeScreen
import com.example.axognition.ui.screens.TestScreen

// Side panel navigation destinations (moved to ui.panels)
import com.example.axognition.ui.panels.ProfilePanelScreen
import com.example.axognition.ui.panels.PerformancePanelScreen
import com.example.axognition.ui.panels.HealthPanelScreen
import com.example.axognition.ui.panels.CalendarPanelScreen
import com.example.axognition.ui.panels.TimePanelScreen
import com.example.axognition.ui.panels.SettingsPanelScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxognitionTheme {
                MainApp()
            }
        }
    }
}

data class DashboardItem(
    val id: Int,
    val title: String,
    val icon: ImageVector
)

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Detail : Screen("detail")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Axognition Menu",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_profile")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Star, contentDescription = "Performance") },
                    label = { Text("Performance") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_performance")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Health") },
                    label = { Text("Health") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_health")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_calendar")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = "Time") },
                    label = { Text("Time") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_time")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_settings")
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Axognition Dashboard") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Drawer"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onItemClick = { itemTitle ->
                            navController.navigate("${Screen.Detail.route}/$itemTitle")
                        }
                    )
                }

                // Side Panel Destinations (from ui.panels folder)
                composable("panel_profile") { ProfilePanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_performance") { PerformancePanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_health") { HealthPanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_calendar") { CalendarPanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_time") { TimePanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_settings") { SettingsPanelScreen(onBack = { navController.popBackStack() }) }

                // Dashboard Feature Routes
                composable("${Screen.Detail.route}/Books") {
                    BooksScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Call-Messages") {
                    CallMessagesScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Courses") {
                    CoursesScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Exersies") {
                    ExercisesScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Games") {
                    GamesScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Homeworks") {
                    HomeworksScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Lectures") {
                    LecturesScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Map") {
                    MapScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Practice") {
                    PracticeScreen(onBack = { navController.popBackStack() })
                }
                composable("${Screen.Detail.route}/Test") {
                    TestScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, onItemClick: (String) -> Unit) {
    var items by remember {
        mutableStateOf(
            listOf(
                DashboardItem(1, "Lectures", Icons.Default.Book),
                DashboardItem(2, "Homeworks", Icons.Default.List),
                DashboardItem(3, "Practice", Icons.Default.Create),
                DashboardItem(4, "Test", Icons.Default.CheckCircle),
                DashboardItem(5, "Courses", Icons.Default.LibraryBooks),
                DashboardItem(6, "Books", Icons.Default.MenuBook),
                DashboardItem(7, "Exersies", Icons.Default.FitnessCenter),
                DashboardItem(8, "Games", Icons.Default.PlayArrow),
                DashboardItem(9, "Call-Messages", Icons.Default.Message),
                DashboardItem(10, "Map", Icons.Default.LocationOn)
            )
        )
    }

    val lazyGridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current

    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        items = items.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val configuration = LocalConfiguration.current
    val columnCount = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome Back",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "Long-press and drag cards to rearrange",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            state = lazyGridState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                ReorderableItem(reorderableLazyGridState, key = item.id) { isDragging ->
                    val elevation = if (isDragging) 12.dp else 2.dp

                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 0.92f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "cardScale"
                    )

                    val currentModifier = Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle()
                        .scale(scale)

                    DashboardCard(
                        item = item,
                        elevation = elevation,
                        modifier = currentModifier
                    ) {
                        onItemClick(item.title)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    item: DashboardItem,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DashboardPreview() {
    AxognitionTheme {
        MainApp()
    }
}