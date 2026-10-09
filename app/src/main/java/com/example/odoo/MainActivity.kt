package com.example.odoo

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.odoo.ui.theme.OdooTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var portalNetworkClient: PortalNetworkClient
    private lateinit var portalRepository: PortalRepository
    private lateinit var viewModel: PortalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        portalNetworkClient = PortalNetworkClient(
            cacheDir = File(cacheDir, "http_cache"),
            getSessionId = { viewModel.sessionId.value }
        )
        portalRepository = PortalRepository(portalNetworkClient.okHttpClient)
        
        val factory = PortalViewModelFactory(portalRepository)
        viewModel = ViewModelProvider(this, factory)[PortalViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }

            OdooTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(
                        baseUrl = "https://app.iqra.edu.pk", 
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

// Custom Colors for Iqra Theme
val IqraNavy = Color(0xFF13284C)
val IqraBlue = Color(0xFF1976D2)
val BackgroundGray = Color(0xFFF5F7FA)
val BadgeBg = Color(0xFFE8F0FE)
val BadgeText = Color(0xFF1565C0)
val TextGray = Color(0xFF5F6368)

@Composable
fun RootScreen(
    baseUrl: String,
    viewModel: PortalViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val sessionId by viewModel.sessionId.collectAsState()

    if (sessionId == null) {
        LoginScreen(baseUrl, viewModel)
    } else {
        MainAppScreen(baseUrl, viewModel, isDarkTheme, onThemeToggle)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(baseUrl: String, viewModel: PortalViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "IQRA UNIVERSITY PORTAL",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 48.dp, start = 16.dp, bottom = 16.dp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )
        
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = AuthWebViewClient(
                        loginUrl = "$baseUrl/web/login", 
                        onSessionExtracted = { extractedId ->
                            viewModel.setSessionId(extractedId)
                        }
                    )
                    loadUrl("$baseUrl/web/login")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    baseUrl: String,
    viewModel: PortalViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "IQRA PORTAL",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = if (isDarkTheme) Color.White else IqraNavy
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = navController.currentDestination?.route == "home",
                    onClick = {
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.DateRange, contentDescription = "Class Schedule") },
                    label = { Text("Class Schedule") },
                    selected = navController.currentDestination?.route == "schedule",
                    onClick = {
                        navController.navigate("schedule") { popUpTo("home") }
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "IQRA ",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                                Text(
                                    text = "UNIVERSITY",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    fontFamily = FontFamily.Serif
                                )
                            }
                            
                            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                            val subtitle = when (currentRoute) {
                                "schedule" -> "Class Schedule"
                                else -> "Dashboard"
                            }
                            
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onThemeToggle) {
                            Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 20.sp)
                        }
                        IconButton(onClick = { viewModel.setSessionId(null) }) {
                            Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = "Logout", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = IqraNavy)
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .padding(innerPadding)
                    .background(if (isDarkTheme) MaterialTheme.colorScheme.background else BackgroundGray)
            ) {
                composable("home") {
                    HomeScreen(isDarkTheme)
                }
                composable("schedule") {
                    ScheduleScreen(baseUrl, viewModel, isDarkTheme)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else TextGray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to Iqra Dashboard",
                style = MaterialTheme.typography.titleLarge,
                color = if (isDarkTheme) Color.White else IqraNavy
            )
        }
    }
}







@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(baseUrl: String, viewModel: PortalViewModel, isDarkTheme: Boolean) {
    val uiState by viewModel.uiState.collectAsState()

    // TRIGGER FETCH ONCE
    LaunchedEffect(Unit) {
        if (uiState is PortalUiState.Idle) {
            viewModel.fetchTimetable(baseUrl)
        }
    }

    when (val state = uiState) {
        is PortalUiState.Idle, is PortalUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IqraBlue)
            }
        }
        is PortalUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.fetchTimetable(baseUrl) }, colors = ButtonDefaults.buttonColors(containerColor = IqraBlue)) {
                    Text("Retry")
                }
            }
        }
        is PortalUiState.Success -> {
            val scheduleMap = state.schedule
            
            if (scheduleMap.isEmpty() || scheduleMap.values.all { it.isEmpty() }) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No timetable entries found.", color = TextGray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    scheduleMap.forEach { (day, entries) ->
                        stickyHeader {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDarkTheme) MaterialTheme.colorScheme.background.copy(alpha = 0.95f) else BackgroundGray.copy(alpha = 0.95f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DateRange,
                                    contentDescription = "Day",
                                    tint = if (isDarkTheme) Color.White else IqraNavy,
                                    modifier = Modifier.size(22.dp).padding(end = 8.dp)
                                )
                                Text(
                                    text = day.trim(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isDarkTheme) Color.White else IqraNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        item {
                            val listState = rememberLazyListState()
                            Box(contentAlignment = Alignment.Center) {
                                LazyRow(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(entries) { entry ->
                                        ModernTimetableCard(
                                            item = entry,
                                            isDarkTheme = isDarkTheme,
                                            modifier = Modifier.fillParentMaxWidth(0.9f) // Fixed size so all cards match!
                                        )
                                    }
                                }
                                
                                // Left Scroll Indicator Arrow
                                if (listState.canScrollBackward) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 4.dp)
                                            .size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                                contentDescription = "Scroll Left",
                                                tint = if (isDarkTheme) Color.White else Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Right Scroll Indicator Arrow
                                if (listState.canScrollForward) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 4.dp)
                                            .size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                contentDescription = "Scroll Right",
                                                tint = if (isDarkTheme) Color.White else Color.Black,
                                                modifier = Modifier.size(24.dp)
                                            )
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
}

@Composable
fun ModernTimetableCard(item: ScheduleItem, isDarkTheme: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left Blue Accent Border
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(IqraBlue)
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                // Time Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer else BadgeBg,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDarkTheme) MaterialTheme.colorScheme.onPrimaryContainer else BadgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Course Title
                Text(
                    text = item.course.toTitleCase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Instructor
                if (item.instructor != "Unknown Instructor") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Instructor",
                            tint = TextGray,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = item.instructor.toTitleCase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                    }
                }
                
                // Room
                if (item.room != "Unknown Room") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "Room",
                            tint = TextGray,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = item.room.toTitleCase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

// Extension to properly case titles like "COMPILER CONSTRUCTION" -> "Compiler Construction"
fun String.toTitleCase(): String {
    return this.lowercase(Locale.ROOT).split(" ").joinToString(" ") { 
        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } 
    }
}
