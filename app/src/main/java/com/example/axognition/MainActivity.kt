package com.example.axognition

import com.example.axognition.ui.tr

import android.content.Context
import com.example.axognition.ui.AppLanguage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.axognition.ui.theme.AxognitionTheme
import com.example.axognition.ui.FloatingAssistantButton
import com.example.axognition.ui.AssistantChatPanel
import com.example.axognition.ui.ChildLoginScreen
import com.example.axognition.ui.AppNavigationMenu
import com.example.axognition.ui.DashboardScreen
import com.example.axognition.ui.WakeWordAssistant
import com.example.axognition.ui.VoiceListeningMode
import com.example.axognition.ui.ChatMessage
import com.example.axognition.data.ChildAuthApi
import com.example.axognition.data.ChildSession
import com.example.axognition.data.ChildSessionStore
import com.example.axognition.data.AssistantConversationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var lecturePlayerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var assistantOpen by rememberSaveable { mutableStateOf(false) }
    var assistantFullScreen by rememberSaveable { mutableStateOf(false) }
    var voiceListeningMode by rememberSaveable { mutableStateOf(VoiceListeningMode.OFF) }
    var chatAudioBusy by remember { mutableStateOf(false) }
    val chatContext = LocalContext.current
    val chatPreferences = remember(childSession.childId) {
        chatContext.getSharedPreferences("assistant_chat_${childSession.childId}", Context.MODE_PRIVATE)
    }
    val conversationStore = remember(childSession.childId) { AssistantConversationStore(chatPreferences) }
    val activeConversation = conversationStore.selected
    val chatMessages = activeConversation.messages
    val appendChatMessage: (ChatMessage) -> Unit = { message ->
        conversationStore.append(activeConversation.id, message)
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
    fun saveAssistantButtonPosition(position: Offset) {
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return
        chatPreferences.edit()
            .putFloat("anchorX", position.x / containerWidthPx)
            .putFloat("anchorY", position.y / containerHeightPx)
            .apply()
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
        // Handwritten calculations contain horizontal strokes, not menu gestures.
        gesturesEnabled = !lecturePlayerOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 340.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                AppNavigationMenu(
                    childName = childSession.displayName,
                    currentRoute = navBackStackEntry?.destination?.route,
                    darkMode = darkMode,
                    onDarkModeChanged = onDarkModeChanged,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            launchSingleTop = true
                            if (route == Screen.Dashboard.route) popUpTo(Screen.Dashboard.route)
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (isDashboard) TopAppBar(
                    title = { Text("Axognition", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = tr("Open Navigation Drawer")
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onDarkModeChanged(!darkMode) }) {
                            Icon(
                                if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = tr(if (darkMode) "Switch to light mode" else "Switch to dark mode")
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
                    LecturesScreen(
                        onBack = { navController.popBackStack() },
                        onPlayerOpenChanged = { lecturePlayerOpen = it }
                    )
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
        WakeWordAssistant(
            conversationId = activeConversation.id,
            listeningMode = voiceListeningMode,
            suspended = chatAudioBusy,
            conversation = { chatMessages.toList() },
            onMessage = appendChatMessage
        ) { voiceBubble, dismissVoice, listenNow ->
            if (!assistantOpen) FloatingAssistantButton(
                anchor = assistantButtonPosition ?: defaultButtonPosition,
                state = voiceBubble,
                onMove = { assistantButtonPosition = it },
                onMoveEnd = { assistantButtonPosition?.let(::saveAssistantButtonPosition) },
                onOpenChat = { assistantOpen = true },
                onDismiss = dismissVoice,
                modifier = Modifier.zIndex(2f)
            )
            AssistantChatPanel(
                expanded = assistantOpen,
                anchor = assistantButtonPosition ?: defaultButtonPosition,
                onMove = { assistantButtonPosition = it },
                onMoveEnd = { assistantButtonPosition?.let(::saveAssistantButtonPosition) },
                messages = chatMessages,
                onMessage = appendChatMessage,
                onDismiss = { assistantOpen = false },
                fullScreen = assistantFullScreen,
                onOpenFullScreen = { assistantFullScreen = true },
                onExitFullScreen = { assistantFullScreen = false },
                listeningMode = voiceListeningMode,
                onListeningModeChange = { voiceListeningMode = it },
                onVoiceInput = listenNow,
                onAudioBusyChange = { chatAudioBusy = it },
                voiceStatus = voiceBubble,
                conversationId = activeConversation.id,
                conversations = conversationStore.conversations,
                onSelectConversation = { id ->
                    dismissVoice()
                    conversationStore.select(id)
                },
                onNewConversation = {
                    dismissVoice()
                    conversationStore.create()
                }
            )
        }
    }
}
