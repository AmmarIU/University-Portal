package com.example.odoo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// Decoupled Data Class representing a clean schedule item
data class ScheduleItem(
    val day: String,
    val time: String,
    val course: String,
    val room: String,
    val instructor: String
)

data class CourseAttendance(
    val courseName: String,
    val courseCode: String,
    val instructorName: String = "Unknown Instructor",
    val classesConducted: Int,
    val classesAttended: Int,
    val academicTerm: String,
    val attendancePercentage: Double,
    val attendanceLogs: List<AttendanceLog>
)

data class AttendanceLog(
    val serialNo: Int,
    val date: String,
    val status: String,
    val fine: String
)

class PortalRepository(private val client: OkHttpClient) {

    suspend fun fetchTimetable(baseUrl: String): Result<List<ScheduleItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/student/class/schedule")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val html = response.body?.string() ?: ""
                val document = Jsoup.parse(html)
                
                val schedule = parseScheduleHtml(document)
                Result.success(schedule)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAttendance(baseUrl: String): Result<List<CourseAttendance>> = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Fetch the Dashboard to find the dedicated Attendance link
            val request = Request.Builder()
                .url("$baseUrl/student/dashboard")
                .get()
                .build()

            val attendancePageUrl = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code} fetching dashboard"))
                }

                val html = response.body?.string() ?: ""
                val document = Jsoup.parse(html)
                
                // Find the specific link that goes to the centralized Attendance page
                // The dashboard might hide or change the link text, so we check multiple variations just in case
                val link = document.select("a[href*=/attendance], a[href*=/student/course]").firstOrNull()?.attr("href")
                
                // Extract Instructor mapping from the dashboard schedule or course list
                val instructorMap = extractInstructorsFromDashboard(document)
                
                if (link.isNullOrEmpty()) {
                    // Log the HTML to see what's actually there if it fails
                    // val debugHtml = document.body().html().take(500)
                    return@withContext Result.failure(Exception("Could not find the main 'Attendance' link on the dashboard."))
                }
                
                // If it picked up a generic course link because /attendance is missing, we can manually construct the attendance URL
                val finalUrl = if (link.contains("/attendance")) {
                    if (link.startsWith("http")) link else "$baseUrl$link"
                } else {
                    // Fallback: forcefully navigate to the known attendance route
                    "$baseUrl/student/attendance"
                }
                
                Pair(finalUrl, instructorMap)
            }
            
            // STEP 2: Fetch that single Attendance page and parse ALL courses from it
            val attendanceRequest = Request.Builder()
                .url(attendancePageUrl.first)
                .get()
                .build()
                
            client.newCall(attendanceRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code} fetching attendance page"))
                }

                val html = response.body?.string() ?: ""
                val courses = parseAllCoursesAttendanceHtml(html, attendancePageUrl.second)
                
                if (courses.isNotEmpty()) {
                    Result.success(courses)
                } else {
                    Result.failure(Exception("Successfully loaded attendance page, but could not parse any courses."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScheduleHtml(document: Document): List<ScheduleItem> {
        val results = mutableListOf<ScheduleItem>()
        val eventBlocks = document.select(".cd-schedule__event, .cd-schedule__events li, .cd-schedule-modal__event-info, a[data-start]")
        val processedNodes = mutableSetOf<org.jsoup.nodes.Element>()
        
        for (block in eventBlocks) {
            if (processedNodes.contains(block)) continue
            
            val text = block.text()
            val timeRegex = Regex("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")
            val timeMatch = timeRegex.find(text)
            val startTime = block.attr("data-start")
            val endTime = block.attr("data-end")
            val timeSlot = timeMatch?.value ?: if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime - $endTime" else ""
            
            if (timeSlot.isNotEmpty()) {
                val lines = block.wholeText().lines()
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.matches(Regex("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")) }
                    .toList()
                    
                val instructor = lines.getOrNull(0) ?: "Unknown Instructor"
                val course = lines.getOrNull(1) ?: "Unknown Course"
                
                val potentialRoomLines = lines.drop(2).filter { 
                    !it.contains("-FA-", ignoreCase = true) && 
                    !it.contains("-SP-", ignoreCase = true) && 
                    !it.contains("IU02", ignoreCase = true) &&
                    !it.contains("BS-CS", ignoreCase = true)
                }
                
                var room = "Unknown Room"
                if (potentialRoomLines.isNotEmpty()) {
                    val combinedRemaining = potentialRoomLines.joinToString(" ")
                    room = combinedRemaining
                } else if (lines.size >= 3) {
                    room = lines.last()
                }
                
                room = room.substringBefore("(").replace(Regex("[)*]"), "").trim()
                if (room.isEmpty()) room = "Unknown Room"
                
                var currentDay = "Schedule"
                val groupParent = block.parents().find { it.hasClass("cd-schedule__group") || it.select(".cd-schedule__top-info").isNotEmpty() }
                if (groupParent != null) {
                    val dayHeader = groupParent.selectFirst(".cd-schedule__top-info span")?.text()?.trim() 
                        ?: groupParent.selectFirst(".cd-schedule__top-info")?.text()?.trim() 
                        ?: ""
                        
                    if (dayHeader.isNotEmpty()) {
                        currentDay = dayHeader
                    }
                }
                
                results.add(ScheduleItem(currentDay, timeSlot, course, room, instructor))
                processedNodes.add(block)
                processedNodes.addAll(block.allElements)
            }
        }
        
        if (results.isEmpty()) {
            results.add(ScheduleItem("Debug", "N/A", "Parsing Failed or No Data Found", "N/A", "Check HTML Structure (Size: ${document.html().length})"))
        }
        
        return results
    }
    
    private fun extractInstructorsFromDashboard(document: Document): Map<String, String> {
        val instructorMap = mutableMapOf<String, String>()
        
        // Find all the cards on the dashboard that contain course information
        val courseCards = document.select(".card-body")
        
        for (card in courseCards) {
            // The course name is in the card header, which is a sibling to card-body
            val header = card.previousElementSibling()
            val courseName = header?.select("span")?.text() ?: continue
            
            // The instructor name is in the h6 tag with class "card-title"
            val instructorName = card.select(".card-title").text()
            
            if (courseName.isNotEmpty() && instructorName.isNotEmpty()) {
                instructorMap[courseName.lowercase().trim()] = instructorName
            }
        }
        
        return instructorMap
    }
}

fun parseAllCoursesAttendanceHtml(html: String, instructorMap: Map<String, String>): List<CourseAttendance> {
    val document = Jsoup.parse(html)
    val results = mutableListOf<CourseAttendance>()
    
    // Find every label that says "Course :"
    val courseLabels = document.select("b:contains(Course :)")
    
    for (label in courseLabels) {
        // The container holding all the info for a single course is usually the closest <li> or `.uk-grid`'s parent
        val gridContainer = label.closest(".uk-grid")
        val mainContainer = gridContainer?.parent() ?: label.closest("li") ?: continue
        
        // 1. Extract Course Info
        val courseName = mainContainer.selectFirst("b:contains(Course :) + span")?.text().orEmpty()
        if (courseName.isEmpty()) continue
        
        val courseCode = mainContainer.selectFirst("b:contains(Course Code :) + span")?.text().orEmpty()
        
        // Prevent duplicates if DOM traversal accidentally loops
        if (results.any { (it.courseCode == courseCode) && it.courseCode.isNotEmpty() }) continue
        
        // 2. Extract Conducted / Attended stats
        val classesConducted = mainContainer.selectFirst("b:contains(Number of classes Conducted :) + span")?.text()?.toIntOrNull() ?: 0
        val classesAttended = mainContainer.selectFirst("b:contains(Number of classes Attended :) + span")?.text()?.toIntOrNull() ?: 0
        
        // 3. Extract Percentage Info
        val academicTerm = mainContainer.selectFirst("b:contains(Academic Term :) + span")?.text().orEmpty()
        val attendancePercentage = mainContainer.selectFirst("b:contains(Attendance Percentage:) + span")?.text()?.toDoubleOrNull() ?: 0.0
        
        // 4. Extract Attendance Logs Table inside this specific container
        val logRows = mainContainer.select("table.uk-table tbody tr")
        val attendanceLogs = mutableListOf<AttendanceLog>()
        
        for (row in logRows) {
            val columns = row.select("td")
            if (columns.size >= 4) {
                val srNo = columns[0].text().toIntOrNull() ?: 0
                val date = columns[1].text()
                val status = columns[2].text()
                val fine = columns[3].text()
                attendanceLogs.add(AttendanceLog(srNo, date, status, fine))
            }
        }
        
        // 5. Construct and add to list
        
        // Find matching instructor using course name or code
        var instructor = "Unknown Instructor"
        
        // Clean up the course name we parsed from the attendance page to make it match the timetable
        val cleanAttendanceCourseName = courseName.lowercase().trim()
        
        // 1. Try an exact match first (This prevents "Course" from eagerly matching "Course (Lab)")
        if (instructorMap.containsKey(cleanAttendanceCourseName)) {
            instructor = instructorMap[cleanAttendanceCourseName]!!
        } else {
            // 2. If no exact match, find the longest partial match to be safe
            val bestMatch = instructorMap.keys
                .filter { cleanAttendanceCourseName.contains(it) || it.contains(cleanAttendanceCourseName) }
                .maxByOrNull { it.length } // Picks "Artificial Intelligence (Lab)" over "Artificial Intelligence"
                
            bestMatch?.let {
                instructor = instructorMap[it]!!
            }
        }
        
        results.add(
            CourseAttendance(
                courseName = courseName,
                courseCode = courseCode,
                instructorName = instructor,
                classesConducted = classesConducted,
                classesAttended = classesAttended,
                academicTerm = academicTerm,
                attendancePercentage = attendancePercentage,
                attendanceLogs = attendanceLogs
            )
        )
    }
    
    return results
}
