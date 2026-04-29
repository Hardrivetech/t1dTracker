package com.hardrivetech.t1dtracker.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.insulin.InsulinCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalculatorUiState(
    val carbs: String = "",
    val icr: String = "",
    val currentGlucose: String = "",
    val targetGlucose: String = "",
    val isf: String = "",
    val rounding: String = "0.5",
    val entries: List<InsulinEntry> = emptyList()
)

@HiltViewModel
class InsulinCalculatorViewModel @Inject constructor(
    private val db: AppDatabase,
    private val prefs: PrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = combine(
        _uiState,
        prefs.defaultICR,
        prefs.defaultISF,
        prefs.defaultTarget
    ) { state, defICR, defISF, defTarget ->
        state.copy(
            icr = if (state.icr.isEmpty() && defICR > 0.0) defICR.toString() else state.icr,
            isf = if (state.isf.isEmpty() && defISF > 0.0) defISF.toString() else state.isf,
            targetGlucose = if (state.targetGlucose.isEmpty() && defTarget > 0.0) defTarget.toString() else state.targetGlucose
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatorUiState())

    init {
        viewModelScope.launch {
            db.insulinDao().getAllFlow().collect { entries ->
                _uiState.value = _uiState.value.copy(entries = entries)
            }
        }
    }

    fun onCarbsChange(value: String) { _uiState.value = _uiState.value.copy(carbs = value) }
    fun onIcrChange(value: String) { _uiState.value = _uiState.value.copy(icr = value) }
    fun onCurrentGlucoseChange(value: String) { _uiState.value = _uiState.value.copy(currentGlucose = value) }
    fun onTargetGlucoseChange(value: String) { _uiState.value = _uiState.value.copy(targetGlucose = value) }
    fun onIsfChange(value: String) { _uiState.value = _uiState.value.copy(isf = value) }
    fun onRoundingChange(value: String) { _uiState.value = _uiState.value.copy(rounding = value) }

    fun saveEntry() {
        viewModelScope.launch {
            val state = _uiState.value
            val carbs = state.carbs.toDoubleOrNull() ?: 0.0
            val icr = state.icr.toDoubleOrNull() ?: 0.0
            val current = state.currentGlucose.toDoubleOrNull() ?: 0.0
            val target = state.targetGlucose.toDoubleOrNull() ?: 0.0
            val isf = state.isf.toDoubleOrNull() ?: 0.0
            val rounding = state.rounding.toDoubleOrNull() ?: 0.5

            val carbDose = if (icr > 0) carbs / icr else 0.0
            val correctionDose = if (isf > 0 && current > target) (current - target) / isf else 0.0
            val totalDose = kotlin.math.round((carbDose + correctionDose) / rounding) * rounding

            val entry = InsulinEntry(
                timestamp = System.currentTimeMillis(),
                carbs = carbs,
                icr = icr,
                currentGlucose = current,
                targetGlucose = target,
                isf = isf,
                carbDose = carbDose,
                correctionDose = correctionDose,
                totalDose = totalDose
            )
            db.insulinDao().insert(entry)
        }
    }
}
