package com.example.odoo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Timeline line colors
val TimelineGray = Color(0xFFE0E0E0)
val TimelineBlue = Color(0xFF03A9F4)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RoadmapScreen(baseUrl: String, viewModel: PortalViewModel, isDarkTheme: Boolean) {
    val uiState by viewModel.roadmapState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState is RoadmapUiState.Idle) {
            viewModel.fetchRoadmap(baseUrl)
        }
    }

    when (val state = uiState) {
        is RoadmapUiState.Idle, is RoadmapUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IqraBlue)
            }
        }
        is RoadmapUiState.Error -> {
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
                    onClick = { viewModel.fetchRoadmap(baseUrl) }, 
                    colors = ButtonDefaults.buttonColors(containerColor = IqraBlue)
                ) {
                    Text("Retry")
                }
            }
        }
        is RoadmapUiState.Success, is RoadmapUiState.Refreshing -> {
            val data = if (state is RoadmapUiState.Success) state.roadmapData else (state as RoadmapUiState.Refreshing).cachedData
            
            val isRefreshing = state is RoadmapUiState.Refreshing
            val pullRefreshState = rememberPullRefreshState(
                refreshing = isRefreshing,
                onRefresh = { viewModel.fetchRoadmap(baseUrl, forceRefresh = true) }
            )
            
            Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
                if (data.semesters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No roadmap entries found.", color = TextGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // User Info Header
                        item {
                            StudentInfoHeader(data, isDarkTheme)
                        }
                        
                        // Dashboard Summary Card
                        item {
                            RoadmapSummaryCard(data, isDarkTheme)
                        }

                        // Semesters & Courses (Timeline)
                        data.semesters.forEach { semester ->
                            item {
                                SemesterHeader(semester, isDarkTheme)
                            }
                            
                            itemsIndexed(semester.courses) { index, course ->
                                val isLastItem = index == semester.courses.lastIndex
                                RoadmapCourseCardWithTimeline(course, isDarkTheme, isLastItem)
                            }
                            
                            item { Spacer(modifier = Modifier.height(16.dp)) }
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
fun StudentInfoHeader(data: RoadmapData, isDarkTheme: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White,
        shadowElevation = 2.dp
    ) {
        Text(
            text = "${data.studentName} • ${data.programName} • Career Year: ${data.careerYear}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (isDarkTheme) Color.LightGray else Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun RoadmapSummaryCard(data: RoadmapData, isDarkTheme: Boolean) {
        val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (screenWidth > 600) 48.dp else 24.dp, vertical = 16.dp), 
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Dashboard Title
                Text("Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
                
                // Career Tag (Top Right)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) Color(0xFF546E7A) else Color(0xFFEEEEEE),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Career: ${data.careerLevel.replace("Under Graduate", "Undergraduate")}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Credits Row
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.completedCreditHours}", 
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Text(
                    text = " / ",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDarkTheme) Color.White else Color.Black
                )
                Text(
                    text = "138",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDarkTheme) Color.White else Color.Black 
                )
                Column(
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = "Cr.",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                    Text(
                        text = "Hrs",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp), // Puts a small gap between them
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Completed /", fontSize = 12.sp, color = TextGray)
                Text("Total", fontSize = 12.sp, color = TextGray)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar (Bottom, full width)
            val progress = (data.completedCreditHours / 138f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = IqraNavy,
                trackColor = if (isDarkTheme) Color.DarkGray else TimelineGray,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp, // Removes the GAP in the PROGRESS BAR
                drawStopIndicator = {} // Removes the DOT at the end of the bar
            )
        }
    }
}

@Composable
fun SemesterHeader(semester: RoadmapSemester, isDarkTheme: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = semester.title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (isDarkTheme) Color.White else Color.Black
        )
        Text(
            text = " (${semester.subtitle})",
            fontSize = 12.sp,
            color = TextGray
        )
        Spacer(modifier = Modifier.weight(1f))
        
        val badgeBg = if (semester.status.equals("Active", ignoreCase = true)) BadgeBg else if (isDarkTheme) Color.DarkGray else Color(0xFFE0E0E0)
        val badgeText = if (semester.status.equals("Active", ignoreCase = true)) BadgeText else if (isDarkTheme) Color.LightGray else Color(0xFF616161)
        
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeBg
        ) {
            Text(
                text = semester.status,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = badgeText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun RoadmapCourseCardWithTimeline(course: RoadmapCourse, isDarkTheme: Boolean, isLastItem: Boolean) {
    val isEnrolled = course.status.equals("Enrolled", ignoreCase = true)
    // Consider completed if the status is not Enrolled, not empty, and not "To be Taken"
    val isCompleted = !isEnrolled && course.status.isNotBlank() && !course.status.contains("To be Taken", ignoreCase = true)
    
    val timelineColor = when {
        isEnrolled -> TimelineBlue
        isCompleted -> SafeGreen
        else -> TimelineGray
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min) // Intrinsic size is safe here because it's just tying the canvas to the card height
    ) {
        // Timeline Graphics (Dot and Line)
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val circleRadius = 4.dp.toPx()
                val topPadding = 24.dp.toPx()
                val centerX = size.width / 2
                
                // Draw connecting line to the next item (unless it's the last item in the semester)
                if (!isLastItem) {
                    drawLine(
                        color = TimelineGray,
                        start = Offset(centerX, topPadding + circleRadius),
                        end = Offset(centerX, size.height + 8.dp.toPx()), // extend a bit below
                        strokeWidth = 2.dp.toPx()
                    )
                }
                
                // Draw the Dot
                drawCircle(
                    color = timelineColor,
                    radius = circleRadius,
                    center = Offset(centerX, topPadding)
                )
            }
        }
        
        // Course Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Active or Completed side strip
                if (isEnrolled || isCompleted) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(if (isEnrolled) TimelineBlue else SafeGreen)
                    )
                }
                
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            // In the mockup, sometimes it's "CSC222 • Operating Systems", sometimes it's "Discrete Structures (CSC241)"
                            // We construct a clean title based on available data
                            val title = if (isEnrolled) "${course.code} • ${course.name}" else "${course.name} (${course.code})"
                            
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${course.type} • ${course.category} • ${course.creditHours} Cr. Hrs",
                                fontSize = 12.sp,
                                color = TextGray
                            )
                        }
                        
                        // Status/Grade Badge
                        val badgeColor = when (course.status) {
                            "Enrolled" -> TimelineBlue
                            "A", "A+", "A-" -> SafeGreen
                            "B+", "B", "B-" -> Color(0xFF689F38)
                            "C+", "C", "C-" -> Color(0xFF827717)
                            "To be Taken ->" -> Color(0xFF546E7A)
                            else -> Color.Gray
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = badgeColor,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = course.status,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "720p Width", device = "spec:width=720px,height=1520px,dpi=320") // Older/Budget phones (e.g. Galaxy A10)
@Preview(name = "1080p Width", device = "spec:width=1080px,height=2340px,dpi=440")
@Preview(name = "1220p Width", device = "spec:width=1220px,height=2712px,dpi=446") // Approx Poco F5 / Redmi Note 13 Pro
@Composable
fun RoadmapSummaryCardPreview() {
    val sampleData = RoadmapData(
        studentName = "Ammar Arif",
        programName = "BACHELORS IN COMPUTER SCIENCE",
        careerYear = "FALL_23",
        completedCreditHours = 92.0f,
        totalCreditHours = 138.0f,
        careerLevel = "Undergraduate",
        semesters = emptyList()
    )
    MaterialTheme {
        Surface {
            RoadmapSummaryCard(data = sampleData, isDarkTheme = false)
        }
    }
}