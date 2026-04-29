package com.hardrivetech.t1dtracker.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.insulin.DoseInput
import com.hardrivetech.t1dtracker.insulin.DoseResult
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
    val entries: List<InsulinEntry> = emptyList(),
    val showConfirmation: Boolean = false,
    val pendingInput: DoseInput? = null,
    val pendingResult: DoseResult? = null
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

    fun onSaveClick() {
        val state = _uiState.value
        val input = DoseInput(
            carbs = state.carbs.toDoubleOrNull() ?: 0.0,
            icr = state.icr.toDoubleOrNull() ?: 0.0,
            currentGlucose = state.currentGlucose.toDoubleOrNull() ?: 0.0,
            targetGlucose = state.targetGlucose.toDoubleOrNull() ?: 0.0,
            isf = state.isf.toDoubleOrNull() ?: 0.0,
            rounding = state.rounding.toDoubleOrNull() ?: 0.5
        )
        val result = InsulinCalculator.calculateDose(input)
        _uiState.value = _uiState.value.copy(
            showConfirmation = true,
            pendingInput = input,
            pendingResult = result
        )
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false)
    }

    fun confirmSave() {
        val input = _uiState.value.pendingInput ?: return
        val result = _uiState.value.pendingResult ?: return
        
        viewModelScope.launch {
            val entry = InsulinEntry(
                timestamp = System.currentTimeMillis(),
                carbs = input.carbs,
                icr = input.icr,
                currentGlucose = input.currentGlucose,
                targetGlucose = input.targetGlucose,
                isf = input.isf,
                carbDose = result.carbDose,
                correctionDose = result.correctionDose,
                totalDose = result.totalDoseRounded
            )
            db.insulinDao().insert(entry)
            _uiState.value = _uiState.value.copy(
                showConfirmation = false,
                carbs = "",
                currentGlucose = ""
            )
        }
    }
}
