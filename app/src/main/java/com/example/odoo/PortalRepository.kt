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

class PortalRepository(private val client: OkHttpClient) {

    suspend fun fetchTimetable(baseUrl: String): Result<List<ScheduleItem>> = withContext(Dispatchers.IO) {
        try {
            // Keep the data source intact using OkHttp
            val request = Request.Builder()
                .url("$baseUrl/student/class/schedule")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                // Pass straight to Jsoup
                val html = response.body?.string() ?: ""
                val document = Jsoup.parse(html)
                
                val schedule = parseScheduleHtml(document)
                Result.success(schedule)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScheduleHtml(document: Document): List<ScheduleItem> {
        val results = mutableListOf<ScheduleItem>()

        // THE WORKING APPROACH!
        // We use the CodyHouse schedule template selectors which successfully found the data previously!
        val eventBlocks = document.select(".cd-schedule__event, .cd-schedule__events li, .cd-schedule-modal__event-info, a[data-start]")
        
        // Keep track to avoid duplicating nodes if selectors overlap
        val processedNodes = mutableSetOf<org.jsoup.nodes.Element>()
        
        for (block in eventBlocks) {
            // Prevent parsing the same event twice
            if (processedNodes.contains(block)) continue
            
            val text = block.text()
            
            // Look for the time pattern (XX:XX - XX:XX)
            val timeRegex = Regex("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")
            val timeMatch = timeRegex.find(text)
            
            // If the text doesn't contain time directly, try attributes (data-start, data-end) which CodyHouse uses
            val startTime = block.attr("data-start")
            val endTime = block.attr("data-end")
            val timeSlot = if (timeMatch != null) timeMatch.value else if (startTime.isNotEmpty() && endTime.isNotEmpty()) "$startTime - $endTime" else ""
            
            if (timeSlot.isNotEmpty()) {
                val lines = block.wholeText().lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.matches(Regex("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")) }
                    
                val instructor = lines.getOrNull(0) ?: "Unknown Instructor"
                val course = lines.getOrNull(1) ?: "Unknown Course"
                
                // We drop the Instructor and Course lines, then filter out the ugly course codes
                val potentialRoomLines = lines.drop(2).filter { 
                    !it.contains("-FA-", ignoreCase = true) && 
                    !it.contains("-SP-", ignoreCase = true) && 
                    !it.contains("IU02", ignoreCase = true) &&
                    !it.contains("BS-CS", ignoreCase = true)
                }
                
                var room = "Unknown Room"
                if (potentialRoomLines.isNotEmpty()) {
                    // Combine the remaining lines in case Jsoup split "GCLAB1" and "( Lab )" onto different lines
                    val combinedRemaining = potentialRoomLines.joinToString(" ")
                    room = combinedRemaining
                } else if (lines.size >= 3) {
                    // Ultimate Fallback: Just grab the absolute last line
                    room = lines.last()
                }
                
                // Clean up the string to remove "( Lecture )", "( Lab )", ")", or "*" 
                room = room.substringBefore("(").replace(Regex("[)*]"), "").trim()
                if (room.isEmpty()) room = "Unknown Room"
                
                // EXTRACT DAY FROM CODYHOUSE STRUCTURE
                var currentDay = "Schedule" // Default if we can't find it
                // CodyHouse groups days into columns usually classed with .cd-schedule__group
                val groupParent = block.parents().find { it.hasClass("cd-schedule__group") || it.select(".cd-schedule__top-info").isNotEmpty() }
                if (groupParent != null) {
                    // selectFirst ensures we only grab the text once, avoiding "Monday Monday" duplicates
                    val dayHeader = groupParent.selectFirst(".cd-schedule__top-info span")?.text()?.trim() 
                        ?: groupParent.selectFirst(".cd-schedule__top-info")?.text()?.trim() 
                        ?: ""
                        
                    if (dayHeader.isNotEmpty()) {
                        currentDay = dayHeader
                    }
                }
                
                results.add(ScheduleItem(currentDay, timeSlot, course, room, instructor))
                processedNodes.add(block)
                // Add all children to processed nodes so we don't parse inner elements again
                processedNodes.addAll(block.allElements)
            }
        }
        
        // Failsafe for debugging if parsing logic completely fails
        if (results.isEmpty()) {
            results.add(ScheduleItem("Debug", "N/A", "Parsing Failed or No Data Found", "N/A", "Check HTML Structure (Size: ${document.html().length})"))
        }
        
        return results
    }
}
