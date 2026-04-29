package com.hardrivetech.t1dtracker

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MigrationConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    lastBackupPath: String?,
    onExportNow: () -> Unit,
    onProceed: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migrate_db_title)) },
        text = {
            Column {
                Text(stringResource(R.string.migration_recommendation))
                lastBackupPath?.let { Text(stringResource(R.string.last_backup_label, it)) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExportNow) { Text(stringResource(R.string.export_now)) }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onProceed) { Text(stringResource(R.string.proceed)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun MigrateInProgressDialog(inProgress: Boolean) {
    if (!inProgress) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.migrating_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.migrating_text))
            }
        },
        confirmButton = {}
    )
}

@Composable
fun RestoreConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedRestoreTimestamp: Long?,
    onConfirmRestore: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_pre_migration)) },
        text = {
            Column {
                val ts = selectedRestoreTimestamp
                if (ts != null) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    Text(stringResource(R.string.restore_backup_question, sdf.format(java.util.Date(ts))))
                } else {
                    Text(stringResource(R.string.restore_latest_question))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.restore_button)) }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ShareBackupButton(
    db: AppDatabase,
    backupPassword: String,
    context: Context,
    scope: CoroutineScope
) {
    Button(modifier = Modifier.fillMaxWidth(), onClick = {
        scope.launch {
            SettingsActions.performShareBackup(db, backupPassword, context)
        }
    }) { Text(stringResource(R.string.share_backup)) }
}

@Composable
fun PrivacyDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(200.dp)
            ) {
                Text("Data storage:")
                val dataStorageMsg = (
                    "- All health data (entries, doses) is stored locally on your device " +
                        "using encrypted prefs and Room database. " +
                        "Backups you create are encrypted with a password you provide."
                    )
                Text(dataStorageMsg)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Telemetry:")
                val telemetryMsg = (
                    "- Crash and usage telemetry is disabled by default. Enabling 'Allow telemetry' " +
                        "permits anonymous crash and analytics collection to help improve the app. " +
                        "No health data is sent in telemetry."
                    )
                Text(telemetryMsg)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Backups & sharing:")
                val backupsMsg = (
                    "- Backups are encrypted with PBKDF2-derived AES-GCM using the password you supply. " +
                        "Keep the password safe; it cannot be recovered by the app."
                    )
                Text(backupsMsg)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Requests & contact:")
                Text("- To request data deletion or help, contact: hardrivetech@proton.me")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun RecoveryGuidanceDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_recommendations_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(200.dp)
            ) {
                Text(stringResource(R.string.backup_rec_1))
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.backup_rec_2))
                Text(stringResource(R.string.backup_rec_3))
                Text(stringResource(R.string.backup_rec_4))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.restore_notes_1))
                Text(stringResource(R.string.restore_notes_2))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun BiometricToggleRow(
    biometricEnabled: Boolean,
    prefs: com.hardrivetech.t1dtracker.data.PrefsRepository,
    context: Context,
    scope: CoroutineScope
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.require_biometric))
        Switch(checked = biometricEnabled, onCheckedChange = { new ->
            scope.launch {
                if (new) {
                    val act = context as? androidx.fragment.app.FragmentActivity
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
}
