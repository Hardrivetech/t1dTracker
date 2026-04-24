package com.hardrivetech.t1dtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(db: AppDatabase) {
    val entriesState = remember { mutableStateOf<List<InsulinEntry>>(emptyList()) }
    LaunchedEffect(db) {
        entriesState.value = db.insulinDao().getAll()
    }

    val scope = rememberCoroutineScope()
    var selectedEntry by remember { mutableStateOf<InsulinEntry?>(null) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<InsulinEntry?>(null) }
    var editCarbsText by remember { mutableStateOf("") }
    var editIcrText by remember { mutableStateOf("") }
    var editCurrentText by remember { mutableStateOf("") }
    var editTargetText by remember { mutableStateOf("") }
    var editIsfText by remember { mutableStateOf("") }
    var editNotesText by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var entriesToExport by remember { mutableStateOf<List<InsulinEntry>>(emptyList()) }

    val filterState = remember { mutableStateOf(7) } // days

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val context = LocalContext.current
        HistoryHeader(
            currentFilter = filterState.value,
            onFilterChange = { filterState.value = it },
            onExport = {
                val now2 = System.currentTimeMillis()
                val msPerDay = 24L * 60L * 60L * 1000L
                val cutoff2 = if (filterState.value == Int.MAX_VALUE) {
                    0L
                } else {
                    now2 - filterState.value * msPerDay
                }
                entriesToExport = entriesState.value.filter { it.timestamp >= cutoff2 }.sortedBy { it.timestamp }
                showExportDialog = true
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val now = System.currentTimeMillis()
        val msPerDay = 24L * 60L * 60L * 1000L
        val cutoff = if (filterState.value == Int.MAX_VALUE) {
            0L
        } else {
            now - filterState.value * msPerDay
        }
        val filtered = entriesState.value.filter { it.timestamp >= cutoff }.sortedBy { it.timestamp }

        if (filtered.isEmpty()) {
            Text("No entries for selected range.")
        } else {
            val points = filtered.map { it.timestamp to it.currentGlucose }
            LineChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) { idx ->
                selectedEntry = filtered.getOrNull(idx)
                showEntryDialog = selectedEntry != null
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Recent entries:", style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(filtered) { e ->
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
                    editCarbsText = e.carbs.toString()
                    editIcrText = e.icr.toString()
                    editCurrentText = e.currentGlucose.toString()
                    editTargetText = e.targetGlucose.toString()
                    editIsfText = e.isf.toString()
                    editNotesText = e.notes ?: ""
                    showEditDialog = true
                    showEntryDialog = false
                },
                onDelete = { e ->
                    scope.launch {
                        db.insulinDao().delete(e)
                        entriesState.value = db.insulinDao().getAll()
                        showEntryDialog = false
                        selectedEntry = null
                    }
                }
            )
        }

        // Edit dialog
        if (showEditDialog && editingEntry != null) {
            EntryEditDialog(
                orig = editingEntry!!,
                onDismiss = { showEditDialog = false; editingEntry = null },
                onSave = { updated ->
                    scope.launch {
                        db.insulinDao().update(updated)
                        entriesState.value = db.insulinDao().getAll()
                        showEditDialog = false
                        editingEntry = null
                    }
                }
            )
        }

        // Export options dialog
        if (showExportDialog) {
            ExportOptionsDialog(entries = entriesToExport, onDismissRequest = { showExportDialog = false })
        }
    }
}

@Composable
private fun HistoryHeader(currentFilter: Int, onFilterChange: (Int) -> Unit, onExport: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onFilterChange(7) }) { Text("7d") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { onFilterChange(30) }) { Text("30d") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { onFilterChange(Int.MAX_VALUE) }) { Text("All") }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onExport) { Text("Export") }
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
        title = { Text("Entry details") },
        text = {
            Column {
                Text("Date: ${formatTime(entry.timestamp)}")
                Text("Carbs: ${entry.carbs} g")
                Text("ICR: ${entry.icr}")
                Text("ISF: ${entry.isf}")
                Text("Current: ${entry.currentGlucose} mg/dL")
                Text("Target: ${entry.targetGlucose} mg/dL")
                Text("Carb dose: ${formatDose(entry.carbDose)} U")
                Text("Correction dose: ${formatDose(entry.correctionDose)} U")
                Text("Total dose: ${formatDose(entry.totalDose)} U")
                if (!entry.notes.isNullOrBlank()) Text("Notes: ${entry.notes}")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(entry) }) { Text("Edit") }
                    TextButton(onClick = { onDelete(entry) }) { Text("Delete", color = Color.Red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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
    var editNotesText by remember { mutableStateOf(orig.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = editCarbsText,
                    onValueChange = { editCarbsText = it },
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editIcrText,
                    onValueChange = { editIcrText = it },
                    label = { Text("ICR (g per unit)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editCurrentText,
                    onValueChange = { editCurrentText = it },
                    label = { Text("Current glucose (mg/dL)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editTargetText,
                    onValueChange = { editTargetText = it },
                    label = { Text("Target glucose (mg/dL)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editIsfText,
                    onValueChange = { editIsfText = it },
                    label = { Text("ISF (mg/dL per unit)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editNotesText,
                    onValueChange = { editNotesText = it },
                    label = { Text("Notes") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newCarbs = editCarbsText.toDoubleOrNull() ?: orig.carbs
                val newIcr = editIcrText.toDoubleOrNull() ?: orig.icr
                val newCurrent = editCurrentText.toDoubleOrNull() ?: orig.currentGlucose
                val newTarget = editTargetText.toDoubleOrNull() ?: orig.targetGlucose
                val newIsf = editIsfText.toDoubleOrNull() ?: orig.isf

                val newCarbDose = if (newIcr > 0.0) newCarbs / newIcr else 0.0
                val newCorrection = if (newIsf > 0.0 && newCurrent > newTarget) {
                    (newCurrent - newTarget) / newIsf
                } else {
                    0.0
                }
                val newTotal = newCarbDose + newCorrection

                val updated = orig.copy(
                    carbs = newCarbs,
                    icr = newIcr,
                    currentGlucose = newCurrent,
                    targetGlucose = newTarget,
                    isf = newIsf,
                    carbDose = newCarbDose,
                    correctionDose = newCorrection,
                    totalDose = newTotal,
                    notes = editNotesText.ifBlank { null }
                )
                onSave(updated)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
