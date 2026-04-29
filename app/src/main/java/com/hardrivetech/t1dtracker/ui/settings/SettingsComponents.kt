package com.hardrivetech.t1dtracker.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hardrivetech.t1dtracker.BiometricAuth
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.SettingsActions
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BiometricToggleRow(
    biometricEnabled: Boolean,
    prefs: PrefsRepository,
    context: Context,
    scope: CoroutineScope
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.require_biometric))
        Switch(
            checked = biometricEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (BiometricAuth.isAvailable(context)) {
                        scope.launch {
                            prefs.setBiometricEnabled(true)
                            Toast.makeText(context, R.string.biometric_enabled, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, R.string.biometric_unavailable, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    scope.launch { prefs.setBiometricEnabled(false) }
                }
            }
        )
    }
}

@Composable
fun ShareBackupButton(
    db: AppDatabase,
    backupPassword: String,
    context: Context,
    scope: CoroutineScope
) {
    Button(
        onClick = {
            if (backupPassword.isBlank()) {
                Toast.makeText(context, "Please set a backup password first", Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    SettingsActions.performShareBackup(db, backupPassword, context)
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.share_backup))
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
