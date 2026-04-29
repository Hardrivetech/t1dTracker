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
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.insulin.DoseInput
import com.hardrivetech.t1dtracker.insulin.DoseResult

@Composable
fun DoseConfirmationDialog(
    input: DoseInput,
    result: DoseResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Dose") },
        text = {
            Column {
                Text("Summary:", style = MaterialTheme.typography.subtitle2)
                Text("Carbs: ${input.carbs} g")
                Text("BG: ${input.currentGlucose} mg/dL")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Calculated Dose:", style = MaterialTheme.typography.subtitle2)
                Text("Carb Dose: ${formatDose(result.carbDose)} U")
                Text("Correction: ${formatDose(result.correctionDose)} U")
                Text("Total: ${formatDose(result.totalDoseRounded)} U", style = MaterialTheme.typography.h6)
                
                if (result.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Warnings:", color = Color.Red, style = MaterialTheme.typography.subtitle2)
                    result.warnings.forEach { warning ->
                        Text("• $warning", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
