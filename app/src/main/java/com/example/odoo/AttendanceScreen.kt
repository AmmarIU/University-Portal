package com.example.odoo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SafeGreen = Color(0xFF388E3C)
val ShortRed = Color(0xFFD32F2F)
val ExcellentBlue = Color(0xFF1976D2)
val BadgeGreenBg = Color(0xFFE8F5E9)
val BadgeRedBg = Color(0xFFFFEBEE)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AttendanceScreen(baseUrl: String, viewModel: PortalViewModel, isDarkTheme: Boolean) {
    val uiState by viewModel.attendanceState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState is AttendanceUiState.Idle) {
            viewModel.fetchAttendance(baseUrl)
        }
    }

    when (val state = uiState) {
        is AttendanceUiState.Idle, is AttendanceUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IqraBlue)
            }
        }
        is AttendanceUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Error: ${state.message}", 
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = { viewModel.fetchAttendance(baseUrl) }, 
                    colors = ButtonDefaults.buttonColors(containerColor = IqraBlue)
                ) {
                    Text("Retry")
                }
            }
        }
        is AttendanceUiState.Success, is AttendanceUiState.Refreshing -> {
            val items = if (state is AttendanceUiState.Success) state.attendanceList else (state as AttendanceUiState.Refreshing).cachedList
            
            // Add Pull Refresh State
            val isRefreshing = state is AttendanceUiState.Refreshing
            val pullRefreshState = rememberPullRefreshState(
                refreshing = isRefreshing,
                onRefresh = { viewModel.fetchAttendance(baseUrl, forceRefresh = true) }
            )
            
            Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No attendance entries found.", color = TextGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            val term = items.firstOrNull()?.academicTerm?.ifEmpty { "Unknown Term" } ?: "Unknown Term"
                            Text(
                                text = "Term: $term",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDarkTheme) Color.White else Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(items, key = { it.courseCode + it.courseName }) { item ->
                            AttendanceCard(item = item, isDarkTheme = isDarkTheme)
                        }
                    }
                }
                
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White,
                    contentColor = IqraBlue
                )
            }
        }
    }
}

@Composable
fun AttendanceCard(item: CourseAttendance, isDarkTheme: Boolean) {
    val percentage = item.attendancePercentage
    val progressColor = when {
        percentage >= 85 -> ExcellentBlue
        percentage >= 75 -> SafeGreen
        else -> ShortRed
    }
    
    // rememberSaveable survives scrolling off-screen
    var isExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // We use IntrinsicSize.Min to ensure the blue strip stretches all the way down
        // but ONLY on the Row itself, which is safer.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Blue Accent Border
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(IqraBlue)
            )
            
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = item.courseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color.Black,
                            lineHeight = 24.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Instructor",
                                tint = TextGray,
                                modifier = Modifier.size(18.dp).padding(end = 4.dp)
                            )
                            Text(text = item.instructorName, color = TextGray, fontSize = 14.sp)
                        }
                    }
                    
                    // Circular Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color.LightGray.copy(alpha = 0.3f),
                            strokeWidth = 6.dp
                        )
                        CircularProgressIndicator(
                            progress = { (percentage / 100).toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            color = progressColor,
                            strokeWidth = 6.dp,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text(
                            text = "${percentage.toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val absentCount = item.classesConducted - item.classesAttended
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Present: ${item.classesAttended}", color = if (isDarkTheme) Color.LightGray else Color.Black, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "Absent: $absentCount", color = if (isDarkTheme) Color.LightGray else Color.Black, fontSize = 14.sp)
                    }
                    
                    // Status Badge
                    val isSafe = percentage >= 75
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSafe) BadgeGreenBg else BadgeRedBg,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSafe) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (isSafe) SafeGreen else ShortRed,
                                modifier = Modifier.size(16.dp).padding(end = 6.dp)
                            )
                            Text(
                                text = if (isSafe) "Safe (Requirement 75%)" else "Short of attendance (Req. 75%)",
                                color = if (isSafe) SafeGreen else ShortRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isDarkTheme) Color.Gray else Color.Black),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isDarkTheme) Color.White else Color.Black)
                ) {
                    Text(if (isExpanded) "Hide Class Log" else "View Class Log")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.AutoMirrored.Outlined.KeyboardArrowRight, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        HorizontalDivider(color = if (isDarkTheme) Color.DarkGray else Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (item.attendanceLogs.isEmpty()) {
                            Text("No class logs available.", color = TextGray, fontSize = 14.sp)
                        } else {
                            item.attendanceLogs.forEach { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF9F9F9)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isDarkTheme) Color.DarkGray else Color(0xFFEEEEEE))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = log.date, fontSize = 12.sp, color = if (isDarkTheme) Color.White else Color.Black)
                                            if (log.fine.isNotBlank() && (log.fine != "-")) {
                                                Text(text = "Fine: ${log.fine}", fontSize = 10.sp, color = ShortRed)
                                            }
                                        }
                                        val statusColor = if (log.status.equals("Present", ignoreCase = true)) SafeGreen else ShortRed
                                        
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = statusColor,
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Text(
                                                text = log.status, 
                                                color = Color.White, 
                                                fontWeight = FontWeight.Bold, 
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
