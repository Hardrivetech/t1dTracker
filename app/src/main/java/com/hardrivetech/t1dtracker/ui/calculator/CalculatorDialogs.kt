package com.hardrivetech.t1dtracker.ui.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.insulin.DoseInput
import com.hardrivetech.t1dtracker.insulin.DoseResult
import com.hardrivetech.t1dtracker.insulin.InsulinCalculator

@Composable
fun DoseConfirmationDialog(
    input: DoseInput,
    result: DoseResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_dose_title)) },
        text = {
            Column {
                Text(stringResource(R.string.summary_label), style = MaterialTheme.typography.subtitle2)
                Text(stringResource(R.string.carbs_val, input.carbs))
                Text(stringResource(R.string.current_bg_val, input.currentGlucose))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.calculated_dose_label), style = MaterialTheme.typography.subtitle2)
                Text("${stringResource(R.string.carb_dose_label)}: ${formatDose(result.carbDose)} U")
                Text("${stringResource(R.string.correction_dose_label)}: ${formatDose(result.correctionDose)} U")
                Text("${stringResource(R.string.total_dose_label, input.rounding)}: ${formatDose(result.totalDoseRounded)} U", style = MaterialTheme.typography.h6)
                
                if (result.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.warnings_label), color = Color.Red, style = MaterialTheme.typography.subtitle2)
                    result.warnings.forEach { warning ->
                        val translatedWarning = when {
                            warning.contains("High dose") -> stringResource(R.string.high_dose_warning, InsulinCalculator.HIGH_DOSE_THRESHOLD.toInt())
                            warning.contains("Extreme carbs") -> stringResource(R.string.extreme_carbs_warning, InsulinCalculator.MAX_CARBS_THRESHOLD.toInt())
                            warning.contains("high BG") -> stringResource(R.string.extreme_high_bg_warning, InsulinCalculator.MAX_BG_THRESHOLD.toInt())
                            warning.contains("low BG") -> stringResource(R.string.extreme_low_bg_warning, InsulinCalculator.MIN_BG_THRESHOLD.toInt())
                            else -> warning
                        }
                        Text("• $translatedWarning", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
