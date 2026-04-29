package com.hardrivetech.t1dtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val entries: List<InsulinEntry> = emptyList(),
    val filterDays: Int = 7
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    private val _filterDays = MutableStateFlow(7)
    
    val uiState: StateFlow<HistoryUiState> = combine(
        db.insulinDao().getAllFlow(),
        _filterDays
    ) { entries, days ->
        val cutoff = if (days == Int.MAX_VALUE) 0L else System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        HistoryUiState(
            entries = entries.filter { it.timestamp >= cutoff },
            filterDays = days
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilterDays(days: Int) {
        _filterDays.value = days
    }

    fun deleteEntry(entry: InsulinEntry) {
        viewModelScope.launch {
            db.insulinDao().delete(entry)
        }
    }

    fun updateEntry(entry: InsulinEntry) {
        viewModelScope.launch {
            db.insulinDao().update(entry)
        }
    }
}
