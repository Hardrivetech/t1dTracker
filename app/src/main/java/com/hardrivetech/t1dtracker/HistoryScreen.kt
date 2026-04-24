package com.hardrivetech.t1dtracker

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint as AndroidPaint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
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
        // permission flow for legacy Downloads save
        val pendingSave = remember { mutableStateOf<Triple<String, String, String>?>(null) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            val p = pendingSave.value
            if (granted && p != null) {
                savePublicLegacy(context, p.first, p.second)
                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_LONG).show()
            } else if (p != null) {
                Toast.makeText(context, "Permission denied; cannot save to Downloads", Toast.LENGTH_LONG).show()
            }
            pendingSave.value = null
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { filterState.value = 7 }) { Text("7d") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { filterState.value = 30 }) { Text("30d") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { filterState.value = Int.MAX_VALUE }) { Text("All") }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                val now2 = System.currentTimeMillis()
                val cutoff2 = if (filterState.value == Int.MAX_VALUE) 0L else now2 - filterState.value * 24L * 60L * 60L * 1000L
                entriesToExport = entriesState.value.filter { it.timestamp >= cutoff2 }.sortedBy { it.timestamp }
                showExportDialog = true
            }) { Text("Export") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val now = System.currentTimeMillis()
        val cutoff = if (filterState.value == Int.MAX_VALUE) 0L else now - filterState.value * 24L * 60L * 60L * 1000L
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
                    Text(
                        "${formatTime(e.timestamp)} — ${formatDose(e.totalDose)} U — ${e.carbs} g — ${e.currentGlucose} mg/dL"
                    )
                }
            }
        }

        if (showEntryDialog && selectedEntry != null) {
            val e = selectedEntry!!
            AlertDialog(
                onDismissRequest = { showEntryDialog = false; selectedEntry = null },
                title = { Text("Entry details") },
                text = {
                    Column {
                        Text("Date: ${formatTime(e.timestamp)}")
                        Text("Carbs: ${e.carbs} g")
                        Text("ICR: ${e.icr}")
                        Text("ISF: ${e.isf}")
                        Text("Current: ${e.currentGlucose} mg/dL")
                        Text("Target: ${e.targetGlucose} mg/dL")
                        Text("Carb dose: ${formatDose(e.carbDose)} U")
                        Text("Correction dose: ${formatDose(e.correctionDose)} U")
                        Text("Total dose: ${formatDose(e.totalDose)} U")
                        if (!e.notes.isNullOrBlank()) Text("Notes: ${e.notes}")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                // prepare edit dialog
                                editingEntry = e
                                editCarbsText = e.carbs.toString()
                                editIcrText = e.icr.toString()
                                editCurrentText = e.currentGlucose.toString()
                                editTargetText = e.targetGlucose.toString()
                                editIsfText = e.isf.toString()
                                editNotesText = e.notes ?: ""
                                showEditDialog = true
                                showEntryDialog = false
                            }) { Text("Edit") }

                            TextButton(onClick = {
                                scope.launch {
                                    db.insulinDao().delete(e)
                                    entriesState.value = db.insulinDao().getAll()
                                    showEntryDialog = false
                                    selectedEntry = null
                                }
                            }) { Text("Delete", color = Color.Red) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEntryDialog = false; selectedEntry = null }) { Text("Close") }
                }
            )
        }

        // Edit dialog
        if (showEditDialog && editingEntry != null) {
            val orig = editingEntry!!
            AlertDialog(
                onDismissRequest = { showEditDialog = false; editingEntry = null },
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
                        // build updated entry
                        val newCarbs = editCarbsText.toDoubleOrNull() ?: orig.carbs
                        val newIcr = editIcrText.toDoubleOrNull() ?: orig.icr
                        val newCurrent = editCurrentText.toDoubleOrNull() ?: orig.currentGlucose
                        val newTarget = editTargetText.toDoubleOrNull() ?: orig.targetGlucose
                        val newIsf = editIsfText.toDoubleOrNull() ?: orig.isf

                        val newCarbDose = if (newIcr > 0.0) newCarbs / newIcr else 0.0
                        val newCorrection = if (newIsf > 0.0 && newCurrent > newTarget) (newCurrent - newTarget) / newIsf else 0.0
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

                        scope.launch {
                            db.insulinDao().update(updated)
                            entriesState.value = db.insulinDao().getAll()
                            showEditDialog = false
                            editingEntry = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false; editingEntry = null }) { Text("Cancel") }
                }
            )
        }

        // Export options dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export format") },
                text = {
                    Column {
                        val now3 = System.currentTimeMillis()
                        Text("Choose format to export ${entriesToExport.size} entries:")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            // CSV as file
                            val csv = buildCsv(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.csv"
                            writeCacheAndShareFile(context, fileName, csv, "text/csv", "t1dTracker CSV export")
                            showExportDialog = false
                        }) { Text("CSV (file)") }

                        TextButton(onClick = {
                            // CSV encrypted as file (device-keystore bound)
                            val csv = buildCsv(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.csv.enc"
                            writeEncryptedCacheAndShareFile(
                                context,
                                fileName,
                                csv,
                                "application/octet-stream",
                                "t1dTracker Encrypted CSV export"
                            )
                            showExportDialog = false
                        }) { Text("CSV Encrypted (file)") }

                        TextButton(onClick = {
                            // CSV as text body
                            val csv = buildCsv(entriesToExport)
                            shareText(context, csv, "t1dTracker CSV export")
                            showExportDialog = false
                        }) { Text("CSV (text)") }

                        TextButton(onClick = {
                            // HTML file
                            val html = buildHtml(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.html"
                            writeCacheAndShareFile(context, fileName, html, "text/html", "t1dTracker HTML export")
                            showExportDialog = false
                        }) { Text("HTML (file)") }

                        TextButton(onClick = {
                            // Plain text body
                            val txt = buildPlainText(entriesToExport)
                            shareText(context, txt, "t1dTracker export")
                            showExportDialog = false
                        }) { Text("Plain text") }

                        TextButton(onClick = {
                            // JSON file
                            val json = buildJson(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.json"
                            writeCacheAndShareFile(
                                context,
                                fileName,
                                json,
                                "application/json",
                                "t1dTracker JSON export"
                            )
                            showExportDialog = false
                        }) { Text("JSON (file)") }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            val csv = buildCsv(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.csv"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToDownloads(context, fileName, csv, "text/csv")
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    savePublicLegacy(context, fileName, csv)
                                } else {
                                    pendingSave.value = Triple(fileName, csv, "text/csv")
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            showExportDialog = false
                        }) { Text("Save to Downloads (CSV)") }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            val csv = buildCsv(entriesToExport)
                            val encName = "t1dtracker_export_$now3.csv.enc"
                            val enc = EncryptionUtil.encryptString(context, csv)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToDownloads(context, encName, enc, "application/octet-stream")
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    savePublicLegacy(context, encName, enc)
                                } else {
                                    pendingSave.value = Triple(encName, enc, "application/octet-stream")
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            showExportDialog = false
                        }) { Text("Save Encrypted to Downloads (CSV)") }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            val html = buildHtml(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.html"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToDownloads(context, fileName, html, "text/html")
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    savePublicLegacy(context, fileName, html)
                                } else {
                                    pendingSave.value = Triple(fileName, html, "text/html")
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            showExportDialog = false
                        }) { Text("Save to Downloads (HTML)") }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            val json = buildJson(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.json"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToDownloads(context, fileName, json, "application/json")
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    savePublicLegacy(context, fileName, json)
                                } else {
                                    pendingSave.value = Triple(fileName, json, "application/json")
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            showExportDialog = false
                        }) { Text("Save to Downloads (JSON)") }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = {
                            val txt = buildPlainText(entriesToExport)
                            val fileName = "t1dtracker_export_$now3.txt"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToDownloads(context, fileName, txt, "text/plain")
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    savePublicLegacy(context, fileName, txt)
                                } else {
                                    pendingSave.value = Triple(fileName, txt, "text/plain")
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            showExportDialog = false
                        }) { Text("Save to Downloads (Text)") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportDialog = false }) { Text("Close") }
                }
            )
        }
    }
}

private fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace(
        "'",
        "&#39;"
    )
}

private fun buildHtml(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>t1dTracker export</title></head><body>")
    sb.append("<h1>t1dTracker export</h1>")
    sb.append(
        "<table border=\"1\" cellpadding=\"4\"><tr><th>id</th><th>timestamp</th><th>datetime</th><th>carbs</th><th>icr</th><th>currentGlucose</th><th>targetGlucose</th><th>isf</th><th>carbDose</th><th>correctionDose</th><th>totalDose</th><th>notes</th></tr>"
    )
    for (e in entries) {
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = escapeHtml(e.notes ?: "")
        sb.append("<tr>")
        sb.append("<td>").append(e.id).append("</td>")
        sb.append("<td>").append(e.timestamp).append("</td>")
        sb.append("<td>").append(escapeHtml(date)).append("</td>")
        sb.append("<td>").append(e.carbs).append("</td>")
        sb.append("<td>").append(e.icr).append("</td>")
        sb.append("<td>").append(e.currentGlucose).append("</td>")
        sb.append("<td>").append(e.targetGlucose).append("</td>")
        sb.append("<td>").append(e.isf).append("</td>")
        sb.append("<td>").append(e.carbDose).append("</td>")
        sb.append("<td>").append(e.correctionDose).append("</td>")
        sb.append("<td>").append(e.totalDose).append("</td>")
        sb.append("<td>").append(notesEscaped).append("</td>")
        sb.append("</tr>")
    }
    sb.append("</table></body></html>")
    return sb.toString()
}

private fun buildJson(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sb.append("[")
    entries.forEachIndexed { idx, e ->
        if (idx > 0) sb.append(',')
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = (e.notes ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
        sb.append("{")
        sb.append("\"id\":${e.id},")
        sb.append("\"timestamp\":${e.timestamp},")
        sb.append("\"datetime\":\"").append(date).append("\",")
        sb.append("\"carbs\":${e.carbs},")
        sb.append("\"icr\":${e.icr},")
        sb.append("\"currentGlucose\":${e.currentGlucose},")
        sb.append("\"targetGlucose\":${e.targetGlucose},")
        sb.append("\"isf\":${e.isf},")
        sb.append("\"carbDose\":${e.carbDose},")
        sb.append("\"correctionDose\":${e.correctionDose},")
        sb.append("\"totalDose\":${e.totalDose},")
        sb.append("\"notes\":\"").append(notesEscaped).append("\"")
        sb.append("}")
    }
    sb.append("]")
    return sb.toString()
}

private fun buildPlainText(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (e in entries) {
        sb.append("Date: ").append(sdf.format(java.util.Date(e.timestamp))).append("\n")
        sb.append("Carbs: ").append(e.carbs).append(" g\n")
        sb.append("ICR: ").append(e.icr).append("\n")
        sb.append("Current: ").append(e.currentGlucose).append(" mg/dL\n")
        sb.append("Target: ").append(e.targetGlucose).append(" mg/dL\n")
        sb.append("ISF: ").append(e.isf).append("\n")
        sb.append("Carb dose: ").append(e.carbDose).append(" U\n")
        sb.append("Correction dose: ").append(e.correctionDose).append(" U\n")
        sb.append("Total dose: ").append(e.totalDose).append(" U\n")
        if (!e.notes.isNullOrBlank()) sb.append("Notes: ").append(e.notes).append("\n")
        sb.append("\n")
    }
    return sb.toString()
}

private fun writeCacheAndShareFile(
    context: Context,
    filename: String,
    content: String,
    mimeType: String,
    subject: String
) {
    try {
        val file = File(context.cacheDir, filename)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: Exception) {
        Toast.makeText(context, "Export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

private fun writeEncryptedCacheAndShareFile(
    context: Context,
    filename: String,
    contentPlain: String,
    mimeType: String,
    subject: String
) {
    try {
        val enc = EncryptionUtil.encryptString(context, contentPlain)
        val file = File(context.cacheDir, filename)
        file.writeText(enc)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: Exception) {
        Toast.makeText(context, "Encrypted export failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

private fun shareText(context: Context, content: String, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (ex: Exception) {
        Toast.makeText(context, "Share failed: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

private fun saveToDownloads(context: Context, filename: String, content: String, mimeType: String) {
    try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            // Older devices: use Files collection as best-effort
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: throw IOException("Failed to create MediaStore record")
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
    } catch (ex: Exception) {
        // fallback: write to cache and inform user
        try {
            val f = File(context.cacheDir, filename)
            f.writeText(content)
            Toast.makeText(context, "Saved to app cache (Downloads failed): ${ex.message}", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Save failed: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun savePublicLegacy(context: Context, filename: String, content: String) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val outFile = File(downloadsDir, filename)
        outFile.writeText(content)
        try {
            val uri = android.net.Uri.fromFile(outFile)
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        } catch (_: Exception) {
        }
        Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
    } catch (ex: Exception) {
        Toast.makeText(context, "Failed to save to Downloads: ${ex.message}", Toast.LENGTH_LONG).show()
    }
}

private fun buildCsv(entries: List<InsulinEntry>): String {
    val sb = StringBuilder()
    sb.append(
        "id,timestamp,datetime,carbs,icr,currentGlucose,targetGlucose,isf,carbDose,correctionDose,totalDose,notes\n"
    )
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (e in entries) {
        val date = sdf.format(java.util.Date(e.timestamp))
        val notesEscaped = e.notes?.replace("\"", "\"\"") ?: ""
        sb.append(
            "${e.id},${e.timestamp},\"${date}\",${e.carbs},${e.icr},${e.currentGlucose},${e.targetGlucose},${e.isf},${e.carbDose},${e.correctionDose},${e.totalDose},\"${notesEscaped}\"\n"
        )
    }
    return sb.toString()
}

@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    yLabelCount: Int = 4,
    onPointSelected: ((Int) -> Unit)? = null
) {
    if (points.isEmpty()) {
        Text("No data to chart")
        return
    }

    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val fullXMin = xs.minOrNull() ?: 0L
    val fullXMax = xs.maxOrNull() ?: (fullXMin + 1L)
    val yMinAll = ys.minOrNull() ?: 0.0
    val yMaxAll = ys.maxOrNull() ?: 1.0

    val density = LocalDensity.current
    val leftPaddingPx = with(density) { 40.dp.toPx() }
    val rightPaddingPx = with(density) { 12.dp.toPx() }
    val topPaddingPx = with(density) { 12.dp.toPx() }
    val bottomPaddingPx = with(density) { 24.dp.toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedOffset by remember { mutableStateOf(Offset.Zero) }

    var visibleXMin by remember { mutableStateOf(fullXMin) }
    var visibleXMax by remember { mutableStateOf(fullXMax) }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(points) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTransformGestures
                    val w = canvasSize.width.toFloat()
                    val chartWidth = w - leftPaddingPx - rightPaddingPx
                    if (chartWidth <= 0f) return@detectTransformGestures

                    val oldMin = visibleXMin.toDouble()
                    val oldMax = visibleXMax.toDouble()
                    val oldRange = oldMax - oldMin
                    if (oldRange <= 0) return@detectTransformGestures

                    val centerX = centroid.x.coerceIn(leftPaddingPx, leftPaddingPx + chartWidth)
                    val centerFrac = ((centerX - leftPaddingPx) / chartWidth).coerceIn(0f, 1f)
                    val centerTime = oldMin + centerFrac * oldRange

                    val newRange = (oldRange / zoom).coerceAtLeast(1.0)
                    var newMin = centerTime - (centerTime - oldMin) / zoom
                    var newMax = centerTime + (oldMax - centerTime) / zoom

                    // apply pan (pan.x positive means finger moved right -> move window left)
                    val timeDelta = (pan.x / chartWidth.toDouble()) * newRange
                    newMin -= timeDelta
                    newMax -= timeDelta

                    // clamp to full data bounds
                    val fullMin = fullXMin.toDouble()
                    val fullMax = fullXMax.toDouble()
                    val visibleRange = newMax - newMin
                    if (visibleRange > (fullMax - fullMin)) {
                        newMin = fullMin
                        newMax = fullMax
                    } else {
                        if (newMin < fullMin) {
                            val shift = fullMin - newMin
                            newMin += shift
                            newMax += shift
                        }
                        if (newMax > fullMax) {
                            val shift = newMax - fullMax
                            newMin -= shift
                            newMax -= shift
                        }
                    }

                    visibleXMin = newMin.toLong().coerceAtLeast(fullXMin)
                    visibleXMax = newMax.toLong().coerceAtMost(fullXMax)
                }
            }
    ) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(points) {
                detectTapGestures { tap ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()
                    val chartWidth = w - leftPaddingPx - rightPaddingPx
                    val chartHeight = h - topPaddingPx - bottomPaddingPx

                    val rawYRange = if (yMaxAll - yMinAll == 0.0) 1.0 else yMaxAll - yMinAll
                    val yMinAdj = yMinAll - 0.1 * rawYRange
                    val yMaxAdj = yMaxAll + 0.1 * rawYRange

                    fun xPos(x: Long): Float {
                        if (visibleXMax == visibleXMin) return leftPaddingPx + chartWidth / 2f
                        return leftPaddingPx + ((x - visibleXMin).toFloat() / (visibleXMax - visibleXMin).toFloat()) * chartWidth
                    }

                    fun yPos(y: Double): Float {
                        val frac = ((y - yMinAdj) / (yMaxAdj - yMinAdj)).toFloat().coerceIn(0f, 1f)
                        return topPaddingPx + (chartHeight * (1f - frac))
                    }

                    // find nearest among visible points
                    var nearestIdx: Int? = null
                    var nearestDist = Float.MAX_VALUE
                    points.forEachIndexed { idx, p ->
                        val px = xPos(p.first)
                        if (px < leftPaddingPx || px > leftPaddingPx + chartWidth) return@forEachIndexed
                        val py = yPos(p.second)
                        val d = (px - tap.x) * (px - tap.x) + (py - tap.y) * (py - tap.y)
                        if (d < nearestDist) {
                            nearestDist = d
                            nearestIdx = idx
                        }
                    }

                    val chosen = nearestIdx
                    if (chosen != null) {
                        selectedIndex = chosen
                        selectedOffset = Offset(xPos(points[chosen].first), yPos(points[chosen].second))
                        onPointSelected?.invoke(chosen)
                    }
                }
            }
        ) {
            val w = size.width
            val h = size.height
            val chartWidth = w - leftPaddingPx - rightPaddingPx
            val chartHeight = h - topPaddingPx - bottomPaddingPx

            val rawYRange = if (yMaxAll - yMinAll == 0.0) 1.0 else yMaxAll - yMinAll
            val yMinAdj = yMinAll - 0.1 * rawYRange
            val yMaxAdj = yMaxAll + 0.1 * rawYRange

            fun xPos(x: Long): Float {
                if (visibleXMax == visibleXMin) return leftPaddingPx + chartWidth / 2f
                return leftPaddingPx + ((x - visibleXMin).toFloat() / (visibleXMax - visibleXMin).toFloat()) * chartWidth
            }

            fun yPos(y: Double): Float {
                val frac = ((y - yMinAdj) / (yMaxAdj - yMinAdj)).toFloat().coerceIn(0f, 1f)
                return topPaddingPx + (chartHeight * (1f - frac))
            }

            // draw grid lines & Y labels
            val labelPaint = AndroidPaint().apply {
                color = android.graphics.Color.BLACK
                textSize = with(density) { 12.sp.toPx() }
                isAntiAlias = true
            }

            val gridColor = Color.Gray.copy(alpha = 0.25f)
            for (i in 0 until yLabelCount) {
                val frac = if (yLabelCount == 1) 0.5f else i.toFloat() / (yLabelCount - 1)
                val yValue = yMaxAdj - frac * (yMaxAdj - yMinAdj)
                val y = yPos(yValue)
                drawLine(
                    color = gridColor,
                    start = Offset(leftPaddingPx, y),
                    end = Offset(leftPaddingPx + chartWidth, y)
                )
                val labelText = if (yValue % 1.0 == 0.0) "%.0f".format(yValue) else "%.1f".format(yValue)
                drawContext.canvas.nativeCanvas.drawText(labelText, 4f, y + labelPaint.textSize / 2f, labelPaint)
            }

            // draw x-axis labels based on visible window (start, mid, end)
            val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
            val xTicks = listOf(visibleXMin, visibleXMin + (visibleXMax - visibleXMin) / 2, visibleXMax)
            for (xt in xTicks) {
                val label = sdf.format(java.util.Date(xt))
                val x = xPos(xt)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - labelPaint.measureText(label) / 2f,
                    h - 4f,
                    labelPaint
                )
            }

            // draw smooth path for visible points
            val pointCords = points.mapIndexedNotNull { _, p ->
                if (p.first !in visibleXMin..visibleXMax) null else Offset(xPos(p.first), yPos(p.second))
            }

            val path = Path()
            if (pointCords.isNotEmpty()) {
                path.moveTo(pointCords[0].x, pointCords[0].y)
                for (i in 1 until pointCords.size) {
                    val prev = pointCords[i - 1]
                    val curr = pointCords[i]
                    val midX = (prev.x + curr.x) / 2f
                    val midY = (prev.y + curr.y) / 2f
                    path.quadraticBezierTo(prev.x, prev.y, midX, midY)
                }
                val last = pointCords.last()
                path.lineTo(last.x, last.y)
            }

            drawPath(path = path, color = Color(0xFF0077CC), style = Stroke(width = 3f, cap = StrokeCap.Round))

            // draw points for visible cords
            points.forEachIndexed { idx, p ->
                if (p.first !in visibleXMin..visibleXMax) return@forEachIndexed
                val pt = Offset(xPos(p.first), yPos(p.second))
                drawCircle(color = Color.Red, radius = 4f, center = pt)
                if (selectedIndex == idx) {
                    drawCircle(color = Color.Yellow, radius = 6f, center = pt)
                    drawLine(
                        color = Color.Gray,
                        start = Offset(pt.x, topPaddingPx),
                        end = Offset(pt.x, topPaddingPx + chartHeight)
                    )
                }
            }

            // border around chart area
            drawRect(
                color = Color.LightGray,
                topLeft = Offset(leftPaddingPx, topPaddingPx),
                size = androidx.compose.ui.geometry.Size(chartWidth, chartHeight),
                style = Stroke(width = 1f)
            )
        }

        // tooltip overlay
        if (selectedIndex != null) {
            val idx = selectedIndex!!
            val p = points[idx]
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(p.first))
            val text = "$date: ${p.second} mg/dL"
            val tooltipDpX = with(density) { selectedOffset.x.toDp() }
            val tooltipDpY = with(density) { (selectedOffset.y - 36f).toDp() }
            Card(modifier = Modifier.offset(x = tooltipDpX, y = tooltipDpY)) {
                Text(text = text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.body2)
            }
        }
    }
}
