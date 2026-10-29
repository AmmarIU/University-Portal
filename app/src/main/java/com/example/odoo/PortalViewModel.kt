package com.example.odoo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PortalUiState {
    object Idle : PortalUiState()
    object Loading : PortalUiState()
    data class Success(val schedule: Map<String, List<ScheduleItem>>) : PortalUiState()
    data class Error(val message: String) : PortalUiState()
}

sealed class AttendanceUiState {
    object Idle : AttendanceUiState()
    object Loading : AttendanceUiState()
    data class Refreshing(val cachedList: List<CourseAttendance>) : AttendanceUiState()
    data class Success(val attendanceList: List<CourseAttendance>) : AttendanceUiState()
    data class Error(val message: String) : AttendanceUiState()
}

class PortalViewModel(
    private val repository: PortalRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PortalUiState>(PortalUiState.Idle)
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    private val _attendanceState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Idle)
    val attendanceState: StateFlow<AttendanceUiState> = _attendanceState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()
    
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    
    private val gson = Gson()

    init {
        loadCachedData()
    }
    
    private fun loadCachedData() {
        // Load Timetable
        val cachedTimetable = sharedPreferences.getString("timetable_cache", null)
        if (cachedTimetable != null) {
            val type = object : TypeToken<List<ScheduleItem>>() {}.type
            val items: List<ScheduleItem> = gson.fromJson(cachedTimetable, type)
            if (items.isNotEmpty()) {
                _uiState.value = PortalUiState.Success(items.groupBy { it.day })
            }
        }
        
        // Load Attendance
        val cachedAttendance = sharedPreferences.getString("attendance_cache", null)
        if (cachedAttendance != null) {
            val type = object : TypeToken<List<CourseAttendance>>() {}.type
            val items: List<CourseAttendance> = gson.fromJson(cachedAttendance, type)
            if (items.isNotEmpty()) {
                _attendanceState.value = AttendanceUiState.Success(items)
            }
        }
    }

    fun setSessionId(id: String?) {
        _sessionId.value = id
        if (id == null) {
            _uiState.value = PortalUiState.Idle
            _attendanceState.value = AttendanceUiState.Idle
            // Optionally clear cache on logout:
            // sharedPreferences.edit().clear().apply()
        }
    }

    fun fetchTimetable(baseUrl: String, forceRefresh: Boolean = false) {
        val lastFetch = sharedPreferences.getLong("last_timetable_fetch", 0L)
        val currentTime = System.currentTimeMillis()
        
        if (forceRefresh) {
            if (currentTime - lastFetch < 5 * 60 * 1000L) {
                val remainingSecs = (5 * 60 * 1000L - (currentTime - lastFetch)) / 1000
                viewModelScope.launch { _toastMessage.emit("Please wait ${remainingSecs / 60}m ${remainingSecs % 60}s before refreshing.") }
                return
            }
        } else if (_uiState.value is PortalUiState.Success) {
            return
        }
        
        viewModelScope.launch {
            if (_uiState.value !is PortalUiState.Success) {
                _uiState.value = PortalUiState.Loading
            }
            val result = repository.fetchTimetable(baseUrl)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                sharedPreferences.edit(commit = true) { 
                    putString("timetable_cache", gson.toJson(items)) 
                    putLong("last_timetable_fetch", System.currentTimeMillis())
                }
                _uiState.value = PortalUiState.Success(items.groupBy { it.day })
            } else if (_uiState.value !is PortalUiState.Success) {
                _uiState.value = PortalUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun fetchAttendance(baseUrl: String, forceRefresh: Boolean = false) {
        val lastFetch = sharedPreferences.getLong("last_attendance_fetch", 0L)
        val currentTime = System.currentTimeMillis()
        
        if (forceRefresh) {
            if (currentTime - lastFetch < 5 * 60 * 1000L) {
                val remainingSecs = (5 * 60 * 1000L - (currentTime - lastFetch)) / 1000
                viewModelScope.launch { _toastMessage.emit("Please wait ${remainingSecs / 60}m ${remainingSecs % 60}s before refreshing.") }
                return
            }
            // Temporarily set to loading to trigger the pull-refresh spinner, 
            // but we need a specific 'Refreshing' state so we don't clear the UI list
            val currentItems = (_attendanceState.value as? AttendanceUiState.Success)?.attendanceList
            _attendanceState.value = AttendanceUiState.Refreshing(currentItems ?: emptyList())
        } else if (_attendanceState.value is AttendanceUiState.Success) {
            return
        }
        
        viewModelScope.launch {
            if (_attendanceState.value !is AttendanceUiState.Refreshing) {
                _attendanceState.value = AttendanceUiState.Loading
            }
            val result = repository.fetchAttendance(baseUrl)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                sharedPreferences.edit(commit = true) { 
                    putString("attendance_cache", gson.toJson(items)) 
                    putLong("last_attendance_fetch", System.currentTimeMillis())
                }
                _attendanceState.value = AttendanceUiState.Success(items)
            } else {
                _attendanceState.value = AttendanceUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}

class PortalViewModelFactory(
    private val repository: PortalRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortalViewModel::class.java)) {
            val prefs = context.getSharedPreferences("odoo_cache", Context.MODE_PRIVATE)
            @Suppress("UNCHECKED_CAST")
            return PortalViewModel(repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
