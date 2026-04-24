package com.hardrivetech.t1dtracker

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.BackupImporter
import com.hardrivetech.t1dtracker.data.BackupUtil
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.data.listMigrationBackups
import com.hardrivetech.t1dtracker.data.restoreMigrationBackup
import com.hardrivetech.t1dtracker.util.PasswordStrength
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(db: AppDatabase, prefs: PrefsRepository, onNavigateBack: () -> Unit) {
    val defaultICR by prefs.defaultICR.collectAsState(initial = 0.0)
    val defaultISF by prefs.defaultISF.collectAsState(initial = 0.0)
    val defaultTarget by prefs.defaultTarget.collectAsState(initial = 0.0)
    var icrDefaultText by remember { mutableStateOf(if (defaultICR > 0.0) defaultICR.toString() else "") }
    var isfDefaultText by remember { mutableStateOf(if (defaultISF > 0.0) defaultISF.toString() else "") }
    var targetDefaultText by remember { mutableStateOf(if (defaultTarget > 0.0) defaultTarget.toString() else "") }
    LaunchedEffect(defaultICR) { icrDefaultText = if (defaultICR > 0.0) defaultICR.toString() else "" }
    LaunchedEffect(defaultISF) { isfDefaultText = if (defaultISF > 0.0) defaultISF.toString() else "" }
    LaunchedEffect(defaultTarget) { targetDefaultText = if (defaultTarget > 0.0) defaultTarget.toString() else "" }

    val telemetryConsent by prefs.telemetryConsent.collectAsState(initial = false)
    val biometricEnabled by prefs.biometricEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var showRecoveryGuidance by remember { mutableStateOf(false) }
    var showLegacyConfirm by remember { mutableStateOf(false) }
    val sharedPrefs = context.getSharedPreferences("t1d_crypto", android.content.Context.MODE_PRIVATE)
    var allowLegacyPref by remember { mutableStateOf(sharedPrefs.getBoolean("allow_legacy_wrapped_encryption", false)) }
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var migrationInProgress by remember { mutableStateOf(false) }
    var lastBackupPath by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var availableBackups by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedRestoreTimestamp by remember { mutableStateOf<Long?>(null) }
    var showRotateConfirm by remember { mutableStateOf(false) }
    var rotatingKeyInProgress by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val entries = db.insulinDao().getAll()
                    val json = BackupUtil.buildJsonBackup(entries)
                    val encrypted = BackupUtil.encryptBackupWithPassword(
                        backupPassword.toCharArray(),
                        json.toByteArray(Charsets.UTF_8)
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(encrypted.toByteArray(Charsets.UTF_8))
                    }
                    true
                } catch (e: Exception) {
                    AppLog.e("SettingsScreen", "Export failed: ${e.message}", e)
                    TelemetryUtil.recordException(e, "Export failed in Settings -> Export backup")
                    false
                }
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.backup_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val ins = context.contentResolver.openInputStream(uri) ?: return@withContext false
                    val tmp = File(context.cacheDir, "import_tmp.t1d")
                    ins.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                    BackupImporter.importEncryptedBackupFile(context, db, tmp, backupPassword.toCharArray())
                } catch (e: Exception) {
                    AppLog.e("SettingsScreen", "Import failed: ${e.message}", e)
                    TelemetryUtil.recordException(e, "Import failed in Settings -> Import backup")
                    false
                }
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.default_settings_title), style = MaterialTheme.typography.h6)
            Row {
                TextButton(onClick = {
                    scope.launch {
                        prefs.setDefaultICR(icrDefaultText.toDoubleOrNull() ?: 0.0)
                        prefs.setDefaultISF(isfDefaultText.toDoubleOrNull() ?: 0.0)
                        prefs.setDefaultTarget(targetDefaultText.toDoubleOrNull() ?: 0.0)
                        onNavigateBack()
                    }
                }) { Text(stringResource(R.string.save)) }
                TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.cancel)) }
            }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxSize()
        ) {
            NumberField(stringResource(R.string.default_icr_label), icrDefaultText) { icrDefaultText = it }
            NumberField(stringResource(R.string.default_isf_label), isfDefaultText) { isfDefaultText = it }
            NumberField(label = stringResource(R.string.default_target_label), value = targetDefaultText) { targetDefaultText = it }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.allow_telemetry), modifier = Modifier.weight(1f))
                Switch(checked = telemetryConsent, onCheckedChange = { new ->
                    scope.launch { prefs.setTelemetryConsent(new) }
                    TelemetryUtil.setTelemetryEnabled(context, new)
                })
            }
            TextButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy_policy_button)) }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.require_biometric), modifier = Modifier.weight(1f))
                Switch(checked = biometricEnabled, onCheckedChange = { new ->
                    scope.launch {
                        if (new) {
                            val act = context as? FragmentActivity
                            if (act == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.biometric_not_supported),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            if (!BiometricAuth.isAvailable(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.biometric_unavailable),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            val ok = BiometricAuth.authenticate(
                                act,
                                "Enable biometric",
                                "Confirm to enable biometric lock"
                            )
                            if (ok) {
                                prefs.setBiometricEnabled(true)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.biometric_enabled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.authentication_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            prefs.setBiometricEnabled(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.biometric_unavailable_disabling),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.allow_legacy_encryption), modifier = Modifier.weight(1f))
                Switch(checked = allowLegacyPref, onCheckedChange = { new ->
                    if (new) {
                        showLegacyConfirm = true
                    } else {
                        sharedPrefs.edit().putBoolean("allow_legacy_wrapped_encryption", false).apply()
                        allowLegacyPref = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.allow_legacy_encryption_disabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }

            if (showLegacyConfirm) {
                AlertDialog(onDismissRequest = { showLegacyConfirm = false }, title = {
                    Text(
                        stringResource(R.string.allow_legacy_encryption_warning_title)
                    )
                }, text = { Text(stringResource(R.string.allow_legacy_encryption_warning_text)) }, confirmButton = {
                        TextButton(onClick = {
                            sharedPrefs.edit().putBoolean("allow_legacy_wrapped_encryption", true).apply()
                            allowLegacyPref = true
                            showLegacyConfirm = false
                            val ok = try { EncryptionUtil.isKeystoreUsable(context) } catch (_: Exception) { false }
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    "Wrap-key available — you can now migrate via 'Migrate DB'",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Keystore unavailable — wrapped-key encryption may not be possible on this device",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text(stringResource(R.string.proceed)) }
                    }, dismissButton = {
                        TextButton(onClick = { showLegacyConfirm = false }) {
                            Text(
                                stringResource(R.string.cancel)
                            )
                        }
                    })
            }

            OutlinedTextField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                label = { Text(stringResource(R.string.backup_password_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            val assessment = PasswordStrength.assess(backupPassword)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = (assessment.score / 5f), modifier = Modifier.weight(1f).height(8.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val strengthColor = when (assessment.score) {
                    0, 1 -> MaterialTheme.colors.error
                    2 -> MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
                    3 -> MaterialTheme.colors.primaryVariant
                    4 -> MaterialTheme.colors.primary
                    else -> MaterialTheme.colors.secondary
                }
                Text("Strength: ${assessment.label}", color = strengthColor)
            }

            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = { showRecoveryGuidance = true }) {
                Text(
                    stringResource(R.string.backup_recommendations_title)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { createDocumentLauncher.launch("backup.t1d") }) {
                    Text(
                        stringResource(R.string.export_backup)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        val entries = withContext(Dispatchers.IO) { db.insulinDao().getAll() }
                        val file = withContext(Dispatchers.IO) {
                            BackupUtil.createEncryptedBackupFile(
                                context,
                                "backup.t1d",
                                entries,
                                backupPassword.toCharArray()
                            )
                        }
                        if (file != null) {
                            try {
                                val authority = "${context.packageName}.fileprovider"
                                val uri = FileProvider.getUriForFile(context, authority, file)
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(share, context.getString(R.string.share_backup))
                                )
                            } catch (e: Exception) {
                                AppLog.e("SettingsScreen", "Share failed: ${e.message}", e)
                                TelemetryUtil.recordException(e, "Share backup failed")
                                Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_export_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) { Text(stringResource(R.string.share_backup)) }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !migrationInProgress,
                    onClick = { showMigrationConfirm = true }
                ) { Text(stringResource(R.string.migrate_db_title)) }

                if (showMigrationConfirm) {
                    AlertDialog(onDismissRequest = { if (!migrationInProgress) showMigrationConfirm = false }, title = {
                        Text(
                            stringResource(R.string.migrate_db_title)
                        )
                    }, text = {
                            Column {
                                Text(stringResource(R.string.migration_recommendation))
                                lastBackupPath?.let { Text(stringResource(R.string.last_backup_label, it)) }
                            }
                        }, confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    scope.launch {
                                        val entries = withContext(Dispatchers.IO) { db.insulinDao().getAll() }
                                        val file = withContext(Dispatchers.IO) {
                                            val fileName = "pre_migration_backup_${System.currentTimeMillis()}.t1d"
                                            BackupUtil.createEncryptedBackupFile(
                                                context,
                                                fileName,
                                                entries,
                                                backupPassword.toCharArray()
                                            )
                                        }
                                        if (file != null) {
                                            lastBackupPath = file.absolutePath
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_saved, file.absolutePath),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.backup_export_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }) { Text(stringResource(R.string.export_now)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {
                                    showMigrationConfirm = false
                                    scope.launch {
                                        migrationInProgress = true
                                        val ok = AppDatabase.migratePlaintextToEncrypted(context, db)
                                        migrationInProgress = false
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.migration_complete),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.migration_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }) { Text(stringResource(R.string.proceed)) }
                            }
                        }, dismissButton = {
                            TextButton(
                                onClick = { if (!migrationInProgress) showMigrationConfirm = false }
                            ) { Text(stringResource(R.string.cancel)) }
                        })
                }

                if (migrationInProgress) {
                    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.migrating_title)) }, text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.migrating_text))
                        }
                    }, confirmButton = {})
                }

                Button(modifier = Modifier.fillMaxWidth(), enabled = !migrationInProgress && !restoreInProgress, onClick = {
                    scope.launch {
                        val backups = withContext(Dispatchers.IO) { listMigrationBackups(context) }
                        availableBackups = backups
                        if (availableBackups.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.no_migration_backups),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            selectedRestoreTimestamp = availableBackups.first()
                            showRestoreConfirm = true
                        }
                    }
                }) { Text(stringResource(R.string.restore_pre_migration)) }

                if (showRestoreConfirm) {
                    AlertDialog(onDismissRequest = { if (!restoreInProgress) showRestoreConfirm = false }, title = {
                        Text(
                            stringResource(R.string.restore_pre_migration)
                        )
                    }, text = {
                            Column {
                                val ts = selectedRestoreTimestamp
                                if (ts != null) {
                                    val sdf = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss",
                                        java.util.Locale.getDefault()
                                    )
                                    Text(stringResource(R.string.restore_backup_question, sdf.format(java.util.Date(ts))))
                                } else {
                                    Text(stringResource(R.string.restore_latest_question))
                                }
                            }
                        }, confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    showRestoreConfirm = false
                                    scope.launch {
                                        restoreInProgress = true
                                        val ok = restoreMigrationBackup(context, selectedRestoreTimestamp ?: 0L)
                                        restoreInProgress = false
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.restore_complete),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.restore_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }) { Text(stringResource(R.string.restore_button)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { if (!restoreInProgress) showRestoreConfirm = false }) {
                                    Text(
                                        stringResource(R.string.cancel)
                                    )
                                }
                            }
                        }, dismissButton = {
                            TextButton(onClick = { if (!restoreInProgress) showRestoreConfirm = false }) {
                                Text(
                                    stringResource(R.string.cancel)
                                )
                            }
                        })
                }

                if (restoreInProgress) {
                    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.migrating_title)) }, text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.migrating_text))
                        }
                    }, confirmButton = {})
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }) {
                    Text(
                        stringResource(R.string.import_backup)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !migrationInProgress && !restoreInProgress && !rotatingKeyInProgress,
                    onClick = { showRotateConfirm = true }
                ) { Text(stringResource(R.string.rotate_key)) }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(onDismissRequest = { showPrivacy = false }, title = {
            Text(
                stringResource(R.string.privacy_policy_title)
            )
        }, text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text("Data storage:")
                    Text(
                        "- All health data (entries, doses) is stored locally on your device using encrypted prefs and Room database. Backups you create are encrypted with a password you provide."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Telemetry:")
                    Text(
                        "- Crash and usage telemetry is disabled by default. Enabling 'Allow telemetry' permits anonymous crash and analytics collection to help improve the app. No health data is sent in telemetry."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Backups & sharing:")
                    Text(
                        "- Backups are encrypted with PBKDF2-derived AES-GCM using the password you supply. Keep the password safe; it cannot be recovered by the app."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Requests & contact:")
                    Text("- To request data deletion or help, contact: hardrivetech@proton.me")
                }
            }, confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.close)) } })
    }

    if (showRecoveryGuidance) {
        AlertDialog(onDismissRequest = { showRecoveryGuidance = false }, title = {
            Text(
                stringResource(R.string.backup_recommendations_title)
            )
        }, text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text(stringResource(R.string.backup_rec_1))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.backup_rec_2))
                    Text(stringResource(R.string.backup_rec_3))
                    Text(stringResource(R.string.backup_rec_4))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.restore_notes_1))
                    Text(stringResource(R.string.restore_notes_2))
                }
            }, confirmButton = {
                TextButton(onClick = { showRecoveryGuidance = false }) {
                    Text(
                        stringResource(R.string.close)
                    )
                }
            })
    }

    if (showRotateConfirm) {
        AlertDialog(onDismissRequest = { if (!rotatingKeyInProgress) showRotateConfirm = false }, title = {
            Text(
                stringResource(R.string.rotate_key)
            )
        }, text = { Text(stringResource(R.string.rotate_key_confirm)) }, confirmButton = {
                TextButton(onClick = {
                    showRotateConfirm = false
                    scope.launch {
                        rotatingKeyInProgress = true
                        val ok = try { withContext(Dispatchers.IO) { EncryptionUtil.rotateKey(context) } } catch (
                            e: Exception
                        ) { false }
                        rotatingKeyInProgress = false
                        if (ok) {
                            Toast.makeText(context, context.getString(R.string.rotate_key_success), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.rotate_key_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) { Text(stringResource(R.string.proceed)) }
            }, dismissButton = {
                TextButton(onClick = { if (!rotatingKeyInProgress) showRotateConfirm = false }) {
                    Text(
                        stringResource(R.string.cancel)
                    )
                }
            })
    }
}
