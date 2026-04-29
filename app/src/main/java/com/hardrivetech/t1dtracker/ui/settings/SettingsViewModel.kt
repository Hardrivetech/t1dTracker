package com.hardrivetech.t1dtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultICR: Double = 0.0,
    val defaultISF: Double = 0.0,
    val defaultTarget: Double = 0.0,
    val telemetryConsent: Boolean = false,
    val biometricEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val db: AppDatabase
) : ViewModel() {

    fun getPrefsRepository() = prefs
    fun getDatabase() = db

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.defaultICR,
        prefs.defaultISF,
        prefs.defaultTarget,
        prefs.telemetryConsent,
        prefs.biometricEnabled
    ) { icr, isf, target, telemetry, biometric ->
        SettingsUiState(icr, isf, target, telemetry, biometric)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateDefaultICR(value: Double) { viewModelScope.launch { prefs.setDefaultICR(value) } }
    fun updateDefaultISF(value: Double) { viewModelScope.launch { prefs.setDefaultISF(value) } }
    fun updateDefaultTarget(value: Double) { viewModelScope.launch { prefs.setDefaultTarget(value) } }
    fun updateTelemetryConsent(value: Boolean) { viewModelScope.launch { prefs.setTelemetryConsent(value) } }
    fun updateBiometricEnabled(value: Boolean) { viewModelScope.launch { prefs.setBiometricEnabled(value) } }
}
