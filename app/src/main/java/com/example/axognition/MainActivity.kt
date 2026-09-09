package com.example.axognition

import com.example.axognition.ui.tr

import android.content.res.Configuration
import android.content.Context
import com.example.axognition.ui.AppLanguage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.axognition.ui.theme.AxognitionTheme
import com.example.axognition.ui.AssistantChatButton
import com.example.axognition.ui.AssistantChatPanel
import com.example.axognition.ui.ChildLoginScreen
import com.example.axognition.ui.WakeWordAssistant
import com.example.axognition.ui.ChatMessage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import com.example.axognition.data.ChildAuthApi
import com.example.axognition.data.ChildSession
import com.example.axognition.data.ChildSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.axognition.ui.panels.TasksPanelScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.initialize(this)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("axognition_preferences", Context.MODE_PRIVATE) }
            var darkMode by rememberSaveable { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
            // The app preference can differ from Android's system theme. Keep
            // the time, battery and navigation icons legible over our surfaces.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        0xFF101722.toInt()
                    ) { darkMode }
                )
            }
            AxognitionTheme(darkTheme = darkMode) {
                MainApp(
                    darkMode = darkMode,
                    onDarkModeChanged = { enabled ->
                        darkMode = enabled
                        preferences.edit().putBoolean("dark_mode", enabled).apply()
                    }
                )
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
fun MainApp(darkMode: Boolean, onDarkModeChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Reading the current language here makes the whole navigation shell
    // recompose immediately after a language chip is selected in Settings.
    val language = AppLanguage.code
    var childSession by remember { mutableStateOf(ChildSessionStore.load(context)) }
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var loginError by rememberSaveable { mutableStateOf<String?>(null) }
    val loginScope = rememberCoroutineScope()

    if (childSession == null) {
        ChildLoginScreen(
            isSigningIn = signingIn,
            error = loginError,
            onSignIn = { username, password ->
                signingIn = true
                loginError = null
                loginScope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { ChildAuthApi.login(username, password) } }
                    result.onSuccess { session ->
                        ChildSessionStore.save(context, session)
                        childSession = session
                    }.onFailure { failure ->
                        loginError = failure.message ?: "Could not sign in."
                    }
                    signingIn = false
                }
            }
        )
        return
    }

    AuthenticatedMainApp(darkMode, onDarkModeChanged, childSession!!)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMainApp(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    childSession: ChildSession
) {
    // Keep already-open destinations in sync with the Settings language chip.
    val language = AppLanguage.code
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isDashboard = navBackStackEntry?.destination?.route == Screen.Dashboard.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    var wakeWordEnabled by rememberSaveable { mutableStateOf(false) }
    val chatContext = LocalContext.current
    val chatPreferences = remember(childSession.childId) {
        chatContext.getSharedPreferences("assistant_chat_${childSession.childId}", Context.MODE_PRIVATE)
    }
    val chatMessages = remember(childSession.childId) {
        mutableStateListOf<ChatMessage>().apply {
            runCatching {
                val saved = org.json.JSONArray(chatPreferences.getString("messages", "[]"))
                for (index in 0 until saved.length()) {
                    val message = saved.getJSONObject(index)
                    add(ChatMessage(message.getString("text"), message.getBoolean("fromStudent")))
                }
            }
        }
    }
    val appendChatMessage: (ChatMessage) -> Unit = { message ->
        chatMessages.add(message)
        val saved = org.json.JSONArray()
        chatMessages.forEach { saved.put(org.json.JSONObject().put("text", it.text).put("fromStudent", it.fromStudent)) }
        chatPreferences.edit().putString("messages", saved.toString()).apply()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val containerWidthPx = with(density) { maxWidth.toPx() }
    val containerHeightPx = with(density) { maxHeight.toPx() }
    val buttonSizePx = with(density) { 56.dp.toPx() }
    val edgeMarginPx = with(density) { 16.dp.toPx() }
    val topMarginPx = with(density) { 72.dp.toPx() }
    val defaultButtonPosition = Offset(
        x = (containerWidthPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx),
        y = topMarginPx
    )
    var assistantButtonPosition by remember {
        mutableStateOf<Offset?>(if (chatPreferences.contains("anchorX")) Offset(
            chatPreferences.getFloat("anchorX", 0.9f) * containerWidthPx,
            chatPreferences.getFloat("anchorY", 0.1f) * containerHeightPx
        ) else null)
    }
    LaunchedEffect(assistantButtonPosition) {
        assistantButtonPosition?.let {
            chatPreferences.edit().putFloat("anchorX", it.x / containerWidthPx)
                .putFloat("anchorY", it.y / containerHeightPx).apply()
        }
    }
    LaunchedEffect(containerWidthPx, containerHeightPx) {
        assistantButtonPosition = assistantButtonPosition?.let { position ->
            Offset(
                x = position.x.coerceIn(edgeMarginPx, (containerWidthPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)),
                y = position.y.coerceIn(edgeMarginPx, (containerHeightPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
            )
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = tr("Axognition Menu"),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = tr("Profile")) },
                    label = { Text(tr("Profile")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_profile")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Star, contentDescription = tr("Performance")) },
                    label = { Text(tr("Performance")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_performance")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = tr("Health")) },
                    label = { Text(tr("Health")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_health")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = tr("Calendar")) },
                    label = { Text(tr("Calendar")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_calendar")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = tr("Tasks")) },
                    label = { Text(tr("Today's Tasks")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_tasks")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = tr("Time")) },
                    label = { Text(tr("Time")) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("panel_time")
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = tr("Settings")) },
                    label = { Text(tr("Settings")) },
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
                if (isDashboard) TopAppBar(
                    title = { Text(tr("${childSession.displayName}'s Axognition")) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = tr("Open Navigation Drawer")
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
                        childName = childSession.displayName,
                        onItemClick = { itemTitle ->
                            navController.navigate("${Screen.Detail.route}/$itemTitle")
                        }
                    )
                }

                // Side Panel Destinations (from ui.panels folder)
                composable("panel_profile") {
                    ProfilePanelScreen(
                        childName = childSession.displayName,
                        grade = childSession.grade,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("panel_performance") { PerformancePanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_health") { HealthPanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_calendar") { CalendarPanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_tasks") { TasksPanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_time") { TimePanelScreen(onBack = { navController.popBackStack() }) }
                composable("panel_settings") {
                    SettingsPanelScreen(
                        darkModeEnabled = darkMode,
                        onDarkModeChanged = onDarkModeChanged,
                        onBack = { navController.popBackStack() }
                    )
                }

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
        if (!assistantOpen) AssistantChatButton(
            onClick = { assistantOpen = !assistantOpen },
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.TopStart)
                .offset {
                    val position = assistantButtonPosition ?: defaultButtonPosition
                    IntOffset(
                        position.x.roundToInt(),
                        position.y.roundToInt()
                    )
                }
                .pointerInput(containerWidthPx, containerHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val current = assistantButtonPosition ?: defaultButtonPosition
                        assistantButtonPosition = Offset(
                            x = (current.x + dragAmount.x).coerceIn(
                                edgeMarginPx,
                                (containerWidthPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                            ),
                            y = (current.y + dragAmount.y).coerceIn(
                                edgeMarginPx,
                                (containerHeightPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                            )
                        )
                    }
                }
        )
            AssistantChatPanel(
                expanded = assistantOpen,
                anchor = assistantButtonPosition ?: defaultButtonPosition,
                onMove = { assistantButtonPosition = it },
                messages = chatMessages,
                onMessage = appendChatMessage,
                onDismiss = { assistantOpen = false },
                wakeWordEnabled = wakeWordEnabled,
                onWakeWordEnabledChange = { wakeWordEnabled = it }
            )
        WakeWordAssistant(
            enabled = wakeWordEnabled,
            conversation = { chatMessages.toList() },
            onMessage = appendChatMessage,
            onOpenChat = { assistantOpen = true }
        )
    }
}

@Composable
fun DashboardScreen(childName: String, modifier: Modifier = Modifier, onItemClick: (String) -> Unit) {
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
            text = tr("Welcome back, $childName"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = tr("Long-press and drag cards to rearrange"),
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
                contentDescription = tr(item.title),
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr(item.title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DashboardPreview() {
    AxognitionTheme(darkTheme = false) {
        MainApp(darkMode = false, onDarkModeChanged = {})
    }
}
