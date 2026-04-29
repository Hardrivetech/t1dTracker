package com.hardrivetech.t1dtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hardrivetech.t1dtracker.LineChart
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.insulin.DoseInput
import com.hardrivetech.t1dtracker.insulin.InsulinCalculator
import com.hardrivetech.t1dtracker.ui.calculator.formatDose
import com.hardrivetech.t1dtracker.ui.calculator.formatTime

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedEntry by remember { mutableStateOf<InsulinEntry?>(null) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<InsulinEntry?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HistoryHeader(
            currentFilter = uiState.filterDays,
            onFilterChange = { viewModel.setFilterDays(it) },
            onExport = { showExportDialog = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.entries.isEmpty()) {
            Text(stringResource(R.string.no_entries_range))
        } else {
            val points = uiState.entries.sortedBy { it.timestamp }.map { it.timestamp to it.currentGlucose }
            LineChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) { idx ->
                selectedEntry = uiState.entries.sortedBy { it.timestamp }.getOrNull(idx)
                showEntryDialog = selectedEntry != null
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.recent_entries), style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.entries) { e ->
                    val itemText = "${formatTime(e.timestamp)} — ${formatDose(e.totalDose)} U — ${e.carbs} g — " +
                        "${e.currentGlucose} mg/dL"
                    Text(itemText)
                }
            }
        }
        if (showEntryDialog && selectedEntry != null) {
            EntryDetailsDialog(
                entry = selectedEntry!!,
                onDismiss = { showEntryDialog = false; selectedEntry = null },
                onEdit = { e ->
                    editingEntry = e
                    showEditDialog = true
                    showEntryDialog = false
                },
                onDelete = { e ->
                    selectedEntry = e
                    showDeleteConfirm = true
                }
            )
        }

        if (showDeleteConfirm && selectedEntry != null) {
            DeleteConfirmationDialog(
                entry = selectedEntry!!,
                onConfirm = {
                    viewModel.deleteEntry(selectedEntry!!)
                    showDeleteConfirm = false
                    selectedEntry = null
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        if (showEditDialog && editingEntry != null) {
            EntryEditDialog(
                orig = editingEntry!!,
                onDismiss = { showEditDialog = false; editingEntry = null },
                onSave = { updated ->
                    viewModel.updateEntry(updated)
                    showEditDialog = false
                    editingEntry = null
                }
            )
        }

        if (showExportDialog) {
            ExportOptionsDialog(entries = uiState.entries, onDismissRequest = { showExportDialog = false })
        }
    }
}

@Composable
private fun HistoryHeader(currentFilter: Int, onFilterChange: (Int) -> Unit, onExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            Button(onClick = { onFilterChange(7) }, enabled = currentFilter != 7) { Text("7d") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onFilterChange(30) }, enabled = currentFilter != 30) { Text("30d") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onFilterChange(Int.MAX_VALUE) }, enabled = currentFilter != Int.MAX_VALUE) { Text("All") }
        }
        Button(onClick = onExport) { Text(stringResource(R.string.export)) }
    }
}

@Composable
private fun EntryDetailsDialog(
    entry: InsulinEntry,
    onDismiss: () -> Unit,
    onEdit: (InsulinEntry) -> Unit,
    onDelete: (InsulinEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_details_title)) },
        text = {
            Column {
                Text(stringResource(R.string.date_label, formatTime(entry.timestamp)))
                Text(stringResource(R.string.carbs_val, entry.carbs))
                Text(stringResource(R.string.icr_val, entry.icr))
                Text(stringResource(R.string.isf_val, entry.isf))
                Text(stringResource(R.string.current_bg_val, entry.currentGlucose))
                Text(stringResource(R.string.target_bg_val, entry.targetGlucose))
                Text("${stringResource(R.string.carb_dose_label)}: ${formatDose(entry.carbDose)} U")
                Text("${stringResource(R.string.correction_dose_label)}: ${formatDose(entry.correctionDose)} U")
                Text("${stringResource(R.string.total_dose_label, entry.icr)}: ${formatDose(entry.totalDose)} U")
                if (!entry.notes.isNullOrBlank()) Text(stringResource(R.string.notes_val, entry.notes))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(entry) }) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { onDelete(entry) }) { Text(stringResource(R.string.delete), color = Color.Red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun EntryEditDialog(orig: InsulinEntry, onDismiss: () -> Unit, onSave: (InsulinEntry) -> Unit) {
    var editCarbsText by remember { mutableStateOf(orig.carbs.toString()) }
    var editIcrText by remember { mutableStateOf(orig.icr.toString()) }
    var editCurrentText by remember { mutableStateOf(orig.currentGlucose.toString()) }
    var editTargetText by remember { mutableStateOf(orig.targetGlucose.toString()) }
    var editIsfText by remember { mutableStateOf(orig.isf.toString()) }
    var editRoundingText by remember { mutableStateOf("0.5") } // Default rounding
    var editNotesText by remember { mutableStateOf(orig.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_entry_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editCarbsText,
                    onValueChange = { editCarbsText = it },
                    label = { Text(stringResource(R.string.carbs_g)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editIcrText,
                    onValueChange = { editIcrText = it },
                    label = { Text(stringResource(R.string.icr_units)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editCurrentText,
                    onValueChange = { editCurrentText = it },
                    label = { Text(stringResource(R.string.current_bg)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editTargetText,
                    onValueChange = { editTargetText = it },
                    label = { Text(stringResource(R.string.target_bg)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editIsfText,
                    onValueChange = { editIsfText = it },
                    label = { Text(stringResource(R.string.isf_units)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editRoundingText,
                    onValueChange = { editRoundingText = it },
                    label = { Text(stringResource(R.string.rounding_label)) },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editNotesText,
                    onValueChange = { editNotesText = it },
                    label = { Text(stringResource(R.string.notes_label)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val input = DoseInput(
                    carbs = editCarbsText.toDoubleOrNull() ?: orig.carbs,
                    icr = editIcrText.toDoubleOrNull() ?: orig.icr,
                    currentGlucose = editCurrentText.toDoubleOrNull() ?: orig.currentGlucose,
                    targetGlucose = editTargetText.toDoubleOrNull() ?: orig.targetGlucose,
                    isf = editIsfText.toDoubleOrNull() ?: orig.isf,
                    rounding = editRoundingText.toDoubleOrNull() ?: 0.5
                )
                val result = InsulinCalculator.calculateDose(input)

                val updated = orig.copy(
                    carbs = input.carbs,
                    icr = input.icr,
                    currentGlucose = input.currentGlucose,
                    targetGlucose = input.targetGlucose,
                    isf = input.isf,
                    carbDose = result.carbDose,
                    correctionDose = result.correctionDose,
                    totalDose = result.totalDoseRounded,
                    notes = editNotesText.ifBlank { null }
                )
                onSave(updated)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    entry: InsulinEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete this entry from ${formatTime(entry.timestamp)}?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = Color.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
