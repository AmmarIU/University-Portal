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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
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
import com.example.odoo.ui.theme.OdooTheme
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PortalScreen(
                        modifier = Modifier.padding(innerPadding),
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

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalScreen(
    modifier: Modifier = Modifier,
    baseUrl: String,
    viewModel: PortalViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val sessionId by viewModel.sessionId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier
        .fillMaxSize()
        .background(if (isDarkTheme) MaterialTheme.colorScheme.background else BackgroundGray)
    ) {
        if (sessionId == null) {
            // LOGIN WEBVIEW
            Text(
                text = "IQRA UNIVERSITY PORTAL",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
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
        } else {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IqraNavy)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "IQRA ",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "UNIVERSITY",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 3.dp),
                            fontFamily = FontFamily.Serif
                        )
                    }
                    Text(
                        text = "Class Schedule",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onThemeToggle) {
                        Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 20.sp)
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.setSessionId(null) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Logout",
                            modifier = Modifier.size(18.dp).padding(end = 6.dp)
                        )
                        Text("Logout")
                    }
                }
            }

            // TRIGGER FETCH
            LaunchedEffect(sessionId) {
                if (sessionId != null && uiState is PortalUiState.Idle) {
                    viewModel.fetchTimetable(baseUrl)
                }
            }

            // UI STATE HANDLING
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
                            Text("No timetable entries found on this page.", color = TextGray)
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
                                items(entries) { item ->
                                    ModernTimetableCard(item, isDarkTheme)
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
fun ModernTimetableCard(item: ScheduleItem, isDarkTheme: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
