package com.hardrivetech.t1dtracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hardrivetech.t1dtracker.R

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Personal Defaults", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        
        DefaultNumberField("Default ICR", uiState.defaultICR) { viewModel.updateDefaultICR(it) }
        DefaultNumberField("Default ISF", uiState.defaultISF) { viewModel.updateDefaultISF(it) }
        DefaultNumberField("Default Target", uiState.defaultTarget) { viewModel.updateDefaultTarget(it) }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        Text("App Settings", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Allow Telemetry", modifier = Modifier.weight(1f))
            Switch(checked = uiState.telemetryConsent, onCheckedChange = { viewModel.updateTelemetryConsent(it) })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Biometric Lock", modifier = Modifier.weight(1f))
            Switch(checked = uiState.biometricEnabled, onCheckedChange = { viewModel.updateBiometricEnabled(it) })
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Calculator")
        }
    }
}

@Composable
fun DefaultNumberField(label: String, value: Double, onValueChange: (Double) -> Unit) {
    OutlinedTextField(
        value = if (value == 0.0) "" else value.toString(),
        onValueChange = { val d = it.toDoubleOrNull() ?: 0.0; onValueChange(d) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
