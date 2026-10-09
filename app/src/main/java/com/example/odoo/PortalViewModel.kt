package com.example.odoo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PortalUiState {
    object Idle : PortalUiState()
    object Loading : PortalUiState()
    data class Success(val schedule: Map<String, List<ScheduleItem>>) : PortalUiState()
    data class Error(val message: String) : PortalUiState()
}

class PortalViewModel(private val repository: PortalRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PortalUiState>(PortalUiState.Idle)
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    fun setSessionId(id: String?) {
        _sessionId.value = id
        if (id == null) {
            _uiState.value = PortalUiState.Idle
        }
    }

    fun fetchTimetable(baseUrl: String) {
        viewModelScope.launch {
            _uiState.value = PortalUiState.Loading
            val result = repository.fetchTimetable(baseUrl)
            if (result.isSuccess) {
                val items = result.getOrNull() ?: emptyList()
                // Offload the grouping logic to the ViewModel!
                val grouped = items.groupBy { it.day }
                _uiState.value = PortalUiState.Success(grouped)
            } else {
                _uiState.value = PortalUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}

class PortalViewModelFactory(private val repository: PortalRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PortalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
