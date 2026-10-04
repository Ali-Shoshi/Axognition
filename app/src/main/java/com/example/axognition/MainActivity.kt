package com.example.axognition

import com.example.axognition.ui.tr

import android.content.Context
import com.example.axognition.ui.AppLanguage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.example.axognition.ui.AppNavigationMenu
import com.example.axognition.ui.DashboardScreen
import com.example.axognition.ui.WakeWordAssistant
import com.example.axognition.ui.VoiceListeningMode
import com.example.axognition.ui.VoiceAssistantBubble
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
            val needsTwoLines = voiceBubble?.let { it.isAnswer || it.isQuestion || it.text.length > 34 } == true
            val textMeasurer = rememberTextMeasurer()
            val listeningTextWidth = with(density) {
                textMeasurer.measure(tr("Listening…"), style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, softWrap = false).size.width.toDp()
            }
            val expandedWidth = minOf(
                if (voiceBubble?.isListening == true) listeningTextWidth + 74.dp
                else if (needsTwoLines) 380.dp else 270.dp,
                maxWidth - 32.dp
            )
            val groupWidth = if (voiceBubble != null) expandedWidth else 56.dp
            val groupHeight = if (needsTwoLines) 78.dp else 56.dp
            val groupWidthPx = with(density) { groupWidth.toPx() }
            val groupHeightPx = with(density) { groupHeight.toPx() }
            val requestedPosition = assistantButtonPosition ?: defaultButtonPosition
            val targetX = requestedPosition.x.coerceIn(
                edgeMarginPx,
                (containerWidthPx - groupWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            )
            val targetY = requestedPosition.y.coerceIn(
                edgeMarginPx,
                (containerHeightPx - groupHeightPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            )
            if (!assistantOpen) Box(
                modifier = Modifier
                    .zIndex(2f)
                    .width(groupWidth)
                    .height(groupHeight)
                    .align(Alignment.TopStart)
                    // Drag updates already arrive on the UI frame. Do not animate
                    // each new target: a transition per pointer event makes the
                    // button visibly trail behind the finger.
                    .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
            ) {
                AnimatedVisibility(
                    visible = voiceBubble != null,
                    enter = fadeIn(tween(160)) + scaleIn(tween(220), transformOrigin = TransformOrigin(0f, .5f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(160), transformOrigin = TransformOrigin(0f, .5f)),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    voiceBubble?.let { state ->
                        VoiceResponseBubble(
                            state = state,
                            onOpenChat = { assistantOpen = true },
                            onDismiss = dismissVoice,
                            modifier = Modifier.fillMaxWidth().padding(start = 42.dp)
                        )
                    }
                }
                AssistantChatButton(
                    onClick = { assistantOpen = true },
                    modifier = Modifier
                        .align(if (needsTwoLines) Alignment.TopStart else Alignment.CenterStart)
                        .then(if (needsTwoLines) Modifier.padding(top = 8.dp) else Modifier)
                        .zIndex(1f)
                        .pointerInput(containerWidthPx, containerHeightPx, groupWidthPx, groupHeightPx) {
                            detectDragGestures(
                                onDragEnd = {
                                    assistantButtonPosition?.let(::saveAssistantButtonPosition)
                                },
                                onDragCancel = {
                                    assistantButtonPosition?.let(::saveAssistantButtonPosition)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val current = assistantButtonPosition ?: defaultButtonPosition
                                assistantButtonPosition = Offset(
                                    x = (current.x.coerceAtMost((containerWidthPx - groupWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)) + dragAmount.x)
                                        .coerceIn(edgeMarginPx, (containerWidthPx - groupWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)),
                                    y = (current.y.coerceAtMost((containerHeightPx - groupHeightPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)) + dragAmount.y)
                                        .coerceIn(edgeMarginPx, (containerHeightPx - groupHeightPx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
                                )
                            }
                        }
                )
            }
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

@Composable
private fun VoiceResponseBubble(
    state: VoiceAssistantBubble,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedText = if (state.isQuestion) "${tr("Your question")}: ${state.text}" else if (state.isAnswer) state.text else tr(state.text)
    val needsTwoLines = state.isAnswer || state.isQuestion || displayedText.length > 34
    val readEnd = state.readThrough.coerceIn(0, displayedText.length)
    val currentStart = state.currentStart.coerceIn(0, displayedText.length)
    val currentEnd = state.currentEnd.coerceIn(currentStart, displayedText.length)
    val currentColor = MaterialTheme.colorScheme.primary
    val highlightedText = buildAnnotatedString {
        append(displayedText)
        if (state.isAnswer && readEnd > 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, readEnd)
        }
        if (state.isAnswer && state.currentStart >= 0 && currentEnd > currentStart) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    color = currentColor,
                    background = currentColor.copy(alpha = 0.12f)
                ),
                currentStart,
                currentEnd
            )
        }
    }
    val scrollState = rememberScrollState()
    var lineStarts by remember(displayedText) { mutableStateOf(emptyList<Int>()) }
    var lineTops by remember(displayedText) { mutableStateOf(emptyList<Int>()) }
    val focusOffset = when {
        state.isQuestion -> displayedText.lastIndex
        state.currentStart >= 0 -> state.currentStart
        state.readThrough > 0 -> state.readThrough - 1
        else -> 0
    }.coerceIn(0, (displayedText.length - 1).coerceAtLeast(0))

    LaunchedEffect(focusOffset, lineStarts, lineTops) {
        if (lineStarts.isEmpty() || lineTops.isEmpty()) return@LaunchedEffect
        val currentLine = lineStarts.indexOfLast { it <= focusOffset }.coerceAtLeast(0)
        val firstVisibleLine = if (lineStarts.size > 2) {
            currentLine.coerceAtMost(lineStarts.lastIndex - 1)
        } else 0
        scrollState.animateScrollTo(lineTops[firstVisibleLine], tween(280))
    }

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = if (state.isQuestion) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (state.isQuestion) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            2.dp,
            if (state.isQuestion) MaterialTheme.colorScheme.tertiary else if (state.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shadowElevation = 5.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = if (state.isListening) 12.dp else 4.dp, top = 7.dp, bottom = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (needsTwoLines) 48.dp else 28.dp)
                    .verticalScroll(scrollState)
                    .clickable(onClick = onOpenChat),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                    onTextLayout = { layout ->
                        val starts = List(layout.lineCount) { layout.getLineStart(it) }
                        val tops = List(layout.lineCount) { layout.getLineTop(it).roundToInt() }
                        if (starts != lineStarts) lineStarts = starts
                        if (tops != lineTops) lineTops = tops
                    }
                )
            }
            if (!state.isListening) IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = tr("Stop speaking and dismiss"),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
