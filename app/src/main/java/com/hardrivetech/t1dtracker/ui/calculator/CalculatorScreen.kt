package com.hardrivetech.t1dtracker.ui.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.insulin.DoseInput
import com.hardrivetech.t1dtracker.insulin.InsulinCalculator

@Composable
fun InsulinCalculatorScreen(
    viewModel: InsulinCalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val currentInput = DoseInput(
        carbs = uiState.carbs.toDoubleOrNull() ?: 0.0,
        icr = uiState.icr.toDoubleOrNull() ?: 0.0,
        currentGlucose = uiState.currentGlucose.toDoubleOrNull() ?: 0.0,
        targetGlucose = uiState.targetGlucose.toDoubleOrNull() ?: 0.0,
        isf = uiState.isf.toDoubleOrNull() ?: 0.0,
        rounding = uiState.rounding.toDoubleOrNull() ?: 0.5
    )
    val result = InsulinCalculator.calculateDose(currentInput)

    if (uiState.showConfirmation && uiState.pendingInput != null && uiState.pendingResult != null) {
        DoseConfirmationDialog(
            input = uiState.pendingInput!!,
            result = uiState.pendingResult!!,
            onConfirm = { viewModel.confirmSave() },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            NumberField(stringResource(R.string.carbs_label), uiState.carbs) { viewModel.onCarbsChange(it) }
            NumberField(stringResource(R.string.icr_label), uiState.icr) { viewModel.onIcrChange(it) }
            Spacer(modifier = Modifier.height(8.dp))
            NumberField(stringResource(R.string.current_glucose_label), uiState.currentGlucose) { viewModel.onCurrentGlucoseChange(it) }
            NumberField(stringResource(R.string.target_glucose_label), uiState.targetGlucose) { viewModel.onTargetGlucoseChange(it) }
            NumberField(stringResource(R.string.isf_label), uiState.isf) { viewModel.onIsfChange(it) }
            NumberField(stringResource(R.string.rounding_label), uiState.rounding) { viewModel.onRoundingChange(it) }

            if (result.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    result.warnings.forEach { warning ->
                        Text("⚠️ $warning", color = Color.Red, style = MaterialTheme.typography.caption)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("${stringResource(R.string.carb_dose_label)}: ${formatDose(result.carbDose)} U")
            Text("${stringResource(R.string.correction_dose_label)}: ${formatDose(result.correctionDose)} U")
            Text(
                stringResource(R.string.total_dose_label, currentInput.rounding) + ": ${formatDose(result.totalDoseRounded)} U",
                style = MaterialTheme.typography.h6
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.onSaveClick() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_entry))
            }

            Spacer(modifier = Modifier.height(24.dp))
            RecentEntriesList(entries = uiState.entries)
        }
    }
}

@Composable
fun RecentEntriesList(entries: List<InsulinEntry>) {
    Text(stringResource(R.string.recent_entries), style = MaterialTheme.typography.subtitle1)
    Spacer(modifier = Modifier.height(8.dp))
    for (e in entries.take(5)) {
        Text("${formatTime(e.timestamp)} — ${formatDose(e.totalDose)} U — ${e.carbs} g")
    }
}

@Composable
fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

fun formatDose(value: Double): String {
    return if (value % 1.0 == 0.0) "%.0f".format(value) else "%.2f".format(value)
}

fun formatTime(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
