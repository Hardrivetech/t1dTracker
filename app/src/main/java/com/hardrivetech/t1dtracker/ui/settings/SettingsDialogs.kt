package com.hardrivetech.t1dtracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hardrivetech.t1dtracker.R

@Composable
fun PrivacyDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.privacy_policy_contact))
                // Add more privacy policy text here if needed
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
fun RecoveryGuidanceDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_recommendations_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("• " + stringResource(R.string.backup_rec_1))
                Text("• " + stringResource(R.string.backup_rec_2))
                Text("• " + stringResource(R.string.backup_rec_3))
                Text("• " + stringResource(R.string.backup_rec_4))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.restore_notes_1))
                Text(stringResource(R.string.restore_notes_2))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

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
                if (lastBackupPath != null) {
                    Text(stringResource(R.string.last_backup_label, lastBackupPath))
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = onProceed) { Text(stringResource(R.string.proceed)) }
                TextButton(onClick = onExportNow) { Text(stringResource(R.string.export_now)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun RestoreConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedRestoreTimestamp: Long?,
    onConfirmRestore: () -> Unit
) {
    if (!show || selectedRestoreTimestamp == null) return
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(selectedRestoreTimestamp))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_pre_migration)) },
        text = {
            Text(stringResource(R.string.restore_backup_question, date))
        },
        confirmButton = {
            TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.restore_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun MigrateInProgressDialog(inProgress: Boolean) {
    if (!inProgress) return
    Dialog(onDismissRequest = {}) {
        androidx.compose.material.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.migrating_title))
                Text(stringResource(R.string.migrating_text), style = MaterialTheme.typography.caption)
            }
        }
    }
}
