package com.hardrivetech.t1dtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
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
            Column {
                val now3 = System.currentTimeMillis()
                Text("Choose format to export ${entries.size} entries:")
                Spacer(modifier = Modifier.height(8.dp))
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
                    val csv = buildCsv(entries)
                    shareText(context, csv, "t1dTracker CSV export")
                    onDismissRequest()
                }) { Text("CSV (text)") }

                TextButton(onClick = {
                    val html = buildHtml(entries)
                    val fileName = "t1dtracker_export_$now3.html"
                    writeCacheAndShareFile(context, fileName, html, "text/html", "t1dTracker HTML export")
                    onDismissRequest()
                }) { Text("HTML (file)") }

                TextButton(onClick = {
                    val txt = buildPlainText(entries)
                    shareText(context, txt, "t1dTracker export")
                    onDismissRequest()
                }) { Text("Plain text") }

                TextButton(onClick = {
                    val json = buildJson(entries)
                    val fileName = "t1dtracker_export_$now3.json"
                    writeCacheAndShareFile(context, fileName, json, "application/json", "t1dTracker JSON export")
                    onDismissRequest()
                }) { Text("JSON (file)") }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val csv = buildCsv(entries)
                    val fileName = "t1dtracker_export_$now3.csv"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(context, fileName, csv, "text/csv")
                    } else {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            savePublicLegacy(context, fileName, csv)
                        } else {
                            pendingSave.value = Triple(fileName, csv, "text/csv")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    onDismissRequest()
                }) { Text("Save to Downloads (CSV)") }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val csv = buildCsv(entries)
                    val encName = "t1dtracker_export_$now3.csv.enc"
                    val enc = EncryptionUtil.encryptString(context, csv)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(context, encName, enc, "application/octet-stream")
                    } else {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            savePublicLegacy(context, encName, enc)
                        } else {
                            pendingSave.value = Triple(encName, enc, "application/octet-stream")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    onDismissRequest()
                }) { Text("Save Encrypted to Downloads (CSV)") }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val html = buildHtml(entries)
                    val fileName = "t1dtracker_export_$now3.html"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(context, fileName, html, "text/html")
                    } else {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            savePublicLegacy(context, fileName, html)
                        } else {
                            pendingSave.value = Triple(fileName, html, "text/html")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    onDismissRequest()
                }) { Text("Save to Downloads (HTML)") }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val json = buildJson(entries)
                    val fileName = "t1dtracker_export_$now3.json"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(context, fileName, json, "application/json")
                    } else {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            savePublicLegacy(context, fileName, json)
                        } else {
                            pendingSave.value = Triple(fileName, json, "application/json")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    onDismissRequest()
                }) { Text("Save to Downloads (JSON)") }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val txt = buildPlainText(entries)
                    val fileName = "t1dtracker_export_$now3.txt"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(context, fileName, txt, "text/plain")
                    } else {
                        val hasWrite = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasWrite) {
                            savePublicLegacy(context, fileName, txt)
                        } else {
                            pendingSave.value = Triple(fileName, txt, "text/plain")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    onDismissRequest()
                }) { Text("Save to Downloads (Text)") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        }
    )
}
