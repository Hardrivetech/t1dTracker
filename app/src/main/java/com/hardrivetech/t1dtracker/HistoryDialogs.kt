package com.hardrivetech.t1dtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hardrivetech.t1dtracker.data.InsulinEntry

@Composable
fun ExportOptionsDialog(entries: List<InsulinEntry>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Export format") },
        text = {
            ExportOptionsDialogContent(entries, onDismissRequest, pendingSave, permissionLauncher)
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        }
    )
}

@Composable
fun ExportOptionsDialogContent(
    entries: List<InsulinEntry>,
    onDismissRequest: () -> Unit,
    pendingSave: MutableState<Triple<String, String, String>?>,
    permissionLauncher: ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val now3 = System.currentTimeMillis()
    Column {
        Text("Choose format to export ${entries.size} entries:")
        Spacer(modifier = Modifier.height(8.dp))
        ExportOptionsFileExports(entries, onDismissRequest, now3)
        Spacer(modifier = Modifier.height(4.dp))
        ExportOptionsShareExports(entries, onDismissRequest)
        Spacer(modifier = Modifier.height(4.dp))
        ExportOptionsSaveToDownloads(entries, onDismissRequest, pendingSave, permissionLauncher, now3)
    }
}

@Composable
fun ExportOptionsFileExports(entries: List<InsulinEntry>, onDismissRequest: () -> Unit, now3: Long) {
    val context = LocalContext.current
    TextButton(onClick = {
        val csv = buildCsv(entries)
        val fileName = "t1dtracker_export_$now3.csv"
        writeCacheAndShareFile(context, fileName, csv, "text/csv", "t1dTracker CSV export")
        onDismissRequest()
    }) { Text("CSV (file)") }

    TextButton(onClick = {
        val csv = buildCsv(entries)
        val fileName = "t1dtracker_export_$now3.csv.enc"
        writeEncryptedCacheAndShareFile(
            context,
            fileName,
            csv,
            "application/octet-stream",
            "t1dTracker Encrypted CSV export"
        )
        onDismissRequest()
    }) { Text("CSV Encrypted (file)") }

    TextButton(onClick = {
        val html = buildHtml(entries)
        val fileName = "t1dtracker_export_$now3.html"
        writeCacheAndShareFile(context, fileName, html, "text/html", "t1dTracker HTML export")
        onDismissRequest()
    }) { Text("HTML (file)") }

    TextButton(onClick = {
        val json = buildJson(entries)
        val fileName = "t1dtracker_export_$now3.json"
        writeCacheAndShareFile(context, fileName, json, "application/json", "t1dTracker JSON export")
        onDismissRequest()
    }) { Text("JSON (file)") }
}

@Composable
fun ExportOptionsShareExports(entries: List<InsulinEntry>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        val csv = buildCsv(entries)
        shareText(context, csv, "t1dTracker CSV export")
        onDismissRequest()
    }) { Text("CSV (text)") }

    TextButton(onClick = {
        val txt = buildPlainText(entries)
        shareText(context, txt, "t1dTracker export")
        onDismissRequest()
    }) { Text("Plain text") }
}

@Composable
fun ExportOptionsSaveToDownloads(
    entries: List<InsulinEntry>,
    onDismissRequest: () -> Unit,
    pendingSave: MutableState<Triple<String, String, String>?>,
    permissionLauncher: ActivityResultLauncher<String>,
    now3: Long
) {
    ExportOptionsSaveToDownloadsCsv(entries, onDismissRequest, pendingSave, permissionLauncher, now3)
    Spacer(modifier = Modifier.height(4.dp))
    ExportOptionsSaveToDownloadsFiles(entries, onDismissRequest, pendingSave, permissionLauncher, now3)
}

@Composable
fun ExportOptionsSaveToDownloadsCsv(
    entries: List<InsulinEntry>,
    onDismissRequest: () -> Unit,
    pendingSave: MutableState<Triple<String, String, String>?>,
    permissionLauncher: ActivityResultLauncher<String>,
    now3: Long
) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = {
        val csv = buildCsv(entries)
        val fileName = "t1dtracker_export_$now3.csv"
        saveToDownloadsOrRequestPermission(
            context,
            fileName,
            csv,
            "text/csv",
            pendingSave,
            permissionLauncher
        )
        onDismissRequest()
    }) { Text("Save to Downloads (CSV)") }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = {
        val csv = buildCsv(entries)
        val encName = "t1dtracker_export_$now3.csv.enc"
        val enc = EncryptionUtil.encryptString(context, csv)
        saveToDownloadsOrRequestPermission(
            context,
            encName,
            enc,
            "application/octet-stream",
            pendingSave,
            permissionLauncher
        )
        onDismissRequest()
    }) { Text("Save Encrypted to Downloads (CSV)") }
}

@Composable
fun ExportOptionsSaveToDownloadsFiles(
    entries: List<InsulinEntry>,
    onDismissRequest: () -> Unit,
    pendingSave: MutableState<Triple<String, String, String>?>,
    permissionLauncher: ActivityResultLauncher<String>,
    now3: Long
) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = {
        val html = buildHtml(entries)
        val fileName = "t1dtracker_export_$now3.html"
        saveToDownloadsOrRequestPermission(
            context,
            fileName,
            html,
            "text/html",
            pendingSave,
            permissionLauncher
        )
        onDismissRequest()
    }) { Text("Save to Downloads (HTML)") }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = {
        val json = buildJson(entries)
        val fileName = "t1dtracker_export_$now3.json"
        saveToDownloadsOrRequestPermission(
            context,
            fileName,
            json,
            "application/json",
            pendingSave,
            permissionLauncher
        )
        onDismissRequest()
    }) { Text("Save to Downloads (JSON)") }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(onClick = {
        val txt = buildPlainText(entries)
        val fileName = "t1dtracker_export_$now3.txt"
        saveToDownloadsOrRequestPermission(
            context,
            fileName,
            txt,
            "text/plain",
            pendingSave,
            permissionLauncher
        )
        onDismissRequest()
    }) { Text("Save to Downloads (Text)") }
}

private fun saveToDownloadsOrRequestPermission(
    context: Context,
    fileName: String,
    content: String,
    mime: String,
    pendingSave: MutableState<Triple<String, String, String>?>,
    permissionLauncher: ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveToDownloads(context, fileName, content, mime)
    } else {
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasWrite) {
            savePublicLegacy(context, fileName, content)
        } else {
            pendingSave.value = Triple(fileName, content, mime)
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
