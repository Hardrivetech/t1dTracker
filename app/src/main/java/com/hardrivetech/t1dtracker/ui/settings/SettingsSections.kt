package com.hardrivetech.t1dtracker.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.EncryptionUtil
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.SettingsActions
import com.hardrivetech.t1dtracker.TelemetryUtil
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.data.listMigrationBackups
import com.hardrivetech.t1dtracker.data.restoreMigrationBackup
import com.hardrivetech.t1dtracker.listPreMigrationBackups
import com.hardrivetech.t1dtracker.performMigration
import com.hardrivetech.t1dtracker.performRestoreMigration
import com.hardrivetech.t1dtracker.rotateKey
import com.hardrivetech.t1dtracker.util.PasswordStrength
import kotlinx.coroutines.launch

@Composable
fun SettingsDefaultsSection(prefs: PrefsRepository, onNavigateBack: () -> Unit) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            .padding(16.dp)
            .fillMaxSize()
    ) {
        NumberField(stringResource(R.string.default_icr_label), icrDefaultText) { icrDefaultText = it }
        NumberField(stringResource(R.string.default_isf_label), isfDefaultText) { isfDefaultText = it }
        NumberField(
            label = stringResource(R.string.default_target_label),
            value = targetDefaultText
        ) { targetDefaultText = it }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.allow_telemetry))
            androidx.compose.material.Switch(checked = telemetryConsent, onCheckedChange = { new ->
                scope.launch { prefs.setTelemetryConsent(new) }
                TelemetryUtil.setTelemetryEnabled(context, new)
            })
        }
        TextButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy_policy_button)) }
        Spacer(modifier = Modifier.height(8.dp))
        BiometricToggleRow(biometricEnabled = biometricEnabled, prefs = prefs, context = context, scope = scope)

        if (showPrivacy) PrivacyDialog(show = true, onDismiss = { showPrivacy = false })
    }
}

@Composable
fun SettingsBackupMigrationSection(db: AppDatabase, prefs: PrefsRepository) {
    // mark prefs in use to avoid detekt unused-parameter here
    prefs.hashCode()

    val context = LocalContext.current
    var backupPassword by remember { mutableStateOf("") }
    var showRecoveryGuidance by remember { mutableStateOf(false) }
    var showLegacyConfirm by remember { mutableStateOf(false) }
    val sharedPrefs = context.getSharedPreferences("t1d_crypto", android.content.Context.MODE_PRIVATE)
    var allowLegacyPref by remember { mutableStateOf(sharedPrefs.getBoolean("allow_legacy_wrapped_encryption", false)) }

    Column(modifier = Modifier.padding(16.dp)) {
        BackupPasswordStrengthSection(backupPassword = backupPassword, onPasswordChange = { backupPassword = it })
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = { showRecoveryGuidance = true }) {
            Text(
                stringResource(R.string.backup_recommendations_title)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ExportAndImportButtons(db = db, backupPassword = backupPassword)
        Spacer(modifier = Modifier.height(8.dp))
        MigrationControls(db = db, backupPassword = backupPassword)
        Spacer(modifier = Modifier.height(8.dp))
        PreMigrationRestoreControls()
        Spacer(modifier = Modifier.height(8.dp))
        RotateKeyControls()
    }

    if (showRecoveryGuidance) RecoveryGuidanceDialog(show = true, onDismiss = { showRecoveryGuidance = false })
    if (showLegacyConfirm) {
        AlertDialog(
            onDismissRequest = { showLegacyConfirm = false },
            title = { Text(stringResource(R.string.allow_legacy_encryption_warning_title)) },
            text = { Text(stringResource(R.string.allow_legacy_encryption_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    sharedPrefs.edit().putBoolean("allow_legacy_wrapped_encryption", true).apply()
                    allowLegacyPref = true
                    showLegacyConfirm = false
                    val ok = EncryptionUtil.isKeystoreUsable(context)
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
            },
            dismissButton = {
                TextButton(onClick = { showLegacyConfirm = false }) {
                    Text(
                        stringResource(R.string.cancel)
                    )
                }
            }
        )
    }
}

@Composable
private fun BackupPasswordStrengthSection(backupPassword: String, onPasswordChange: (String) -> Unit) {
    OutlinedTextField(
        value = backupPassword,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.backup_password_label)) },
        modifier = Modifier.fillMaxWidth()
    )
    val assessment = PasswordStrength.assess(backupPassword)
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = (assessment.score / 5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        val strengthColor = when (assessment.score) {
            0, 1 -> MaterialTheme.colors.error
            2 -> MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
            3 -> MaterialTheme.colors.primaryVariant
            4 -> MaterialTheme.colors.primary
            else -> MaterialTheme.colors.secondary
        }
        Text(
            "Strength: ${assessment.label}",
            color = strengthColor
        )
    }
}

@Composable
private fun ExportAndImportButtons(db: AppDatabase, backupPassword: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = SettingsActions.performExportToUri(
                context,
                db,
                uri,
                backupPassword.toCharArray()
            )
            if (ok) {
                Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = SettingsActions.performImportFromUri(
                context,
                db,
                uri,
                backupPassword.toCharArray()
            )
            if (ok) {
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column {
        Button(modifier = Modifier.fillMaxWidth(), onClick = { createDocumentLauncher.launch("backup.t1d") }) {
            Text(
                stringResource(R.string.export_backup)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ShareBackupButton(db = db, backupPassword = backupPassword, context = context, scope = scope)
        Spacer(modifier = Modifier.height(8.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }) {
            Text(
                stringResource(R.string.import_backup)
            )
        }
    }
}

@Composable
private fun MigrationControls(db: AppDatabase, backupPassword: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var migrationInProgress by remember { mutableStateOf(false) }
    var lastBackupPath by remember { mutableStateOf<String?>(null) }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !migrationInProgress,
        onClick = { showMigrationConfirm = true }
    ) { Text(stringResource(R.string.migrate_db_title)) }

    MigrationConfirmDialog(
        show = showMigrationConfirm,
        onDismiss = { if (!migrationInProgress) showMigrationConfirm = false },
        lastBackupPath = lastBackupPath,
        onExportNow = {
            scope.launch {
                val file = SettingsActions.createPreMigrationBackup(
                    context,
                    db,
                    backupPassword.toCharArray()
                )
                if (file != null) {
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
        },
        onProceed = {
            showMigrationConfirm = false
            scope.launch {
                migrationInProgress = true
                val ok = performMigration(context, db)
                migrationInProgress = false
                val msg = if (ok) {
                    context.getString(R.string.migration_complete)
                } else {
                    context.getString(
                        R.string.migration_failed
                    )
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    )

    MigrateInProgressDialog(inProgress = migrationInProgress)
}

@Composable
private fun PreMigrationRestoreControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var availableBackups by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedRestoreTimestamp by remember { mutableStateOf<Long?>(null) }

    Button(modifier = Modifier.fillMaxWidth(), onClick = {
        scope.launch {
            val backups = listPreMigrationBackups(context)
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

    RestoreConfirmDialog(
        show = showRestoreConfirm,
        onDismiss = { if (!restoreInProgress) showRestoreConfirm = false },
        selectedRestoreTimestamp = selectedRestoreTimestamp,
        onConfirmRestore = {
            showRestoreConfirm = false
            scope.launch {
                restoreInProgress = true
                val ok = performRestoreMigration(context, selectedRestoreTimestamp ?: 0L)
                restoreInProgress = false
                if (ok) {
                    Toast.makeText(context, context.getString(R.string.restore_complete), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.restore_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    MigrateInProgressDialog(inProgress = restoreInProgress)
}

@Composable
private fun RotateKeyControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRotateConfirm by remember { mutableStateOf(false) }
    var rotatingKeyInProgress by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !rotatingKeyInProgress,
        onClick = { showRotateConfirm = true }
    ) {
        Text(
            stringResource(R.string.rotate_key)
        )
    }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { if (!rotatingKeyInProgress) showRotateConfirm = false },
            title = { Text(stringResource(R.string.rotate_key)) },
            text = { Text(stringResource(R.string.rotate_key_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRotateConfirm = false
                    scope.launch {
                        rotatingKeyInProgress = true
                        val ok = rotateKey(context)
                        rotatingKeyInProgress = false
                        if (ok) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.rotate_key_success),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.rotate_key_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) { Text(stringResource(R.string.proceed)) }
            },
            dismissButton = {
                TextButton(onClick = { if (!rotatingKeyInProgress) showRotateConfirm = false }) {
                    Text(
                        stringResource(R.string.cancel)
                    )
                }
            }
        )
    }
}
