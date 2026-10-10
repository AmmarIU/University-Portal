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

data class AttendanceItem(
    val courseName: String,
    val percentage: String,
    val totalClasses: String,
    val details: String
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

    suspend fun fetchAttendance(baseUrl: String): Result<List<AttendanceItem>> = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Fetch the Dashboard to hunt for the Course IDs
            val request = Request.Builder()
                .url("$baseUrl/student/dashboard") // Using the standard dashboard route
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val html = response.body?.string() ?: ""
                val document = Jsoup.parse(html)
                
                // Look for any links on the dashboard that lead to a course or attendance page
                val courseLinks = document.select("a[href*=/student/course]").map { it.attr("href") }.toSet()
                
                val results = mutableListOf<AttendanceItem>()
                
                if (courseLinks.isEmpty()) {
                    results.add(AttendanceItem("Discovery Failed", "0%", "0", "Could not find any course links on the dashboard. Are they hidden in a dropdown?"))
                } else {
                    results.add(AttendanceItem("Discovery Success!", "100%", "${courseLinks.size}", "Found course links:\n${courseLinks.joinToString("\n")}"))
                }
                
                Result.success(results)
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
            val timeSlot = if (timeMatch != null) timeMatch.value else if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime - $endTime" else ""
            
            if (timeSlot.isNotEmpty()) {
                val lines = block.wholeText().lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.matches(Regex("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")) }
                    
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

    private fun parseAttendanceHtml(document: Document): List<AttendanceItem> {
        // We won't need this until Step 2!
        return emptyList()
    }
}
