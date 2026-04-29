package com.hardrivetech.t1dtracker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.shareText
import com.hardrivetech.t1dtracker.buildPlainText

@Composable
fun ExportOptionsDialog(
    entries: List<InsulinEntry>,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Export Options") },
        text = {
            Column {
                Text("Choose export format:")
                TextButton(onClick = {
                    val text = buildPlainText(entries)
                    shareText(context, text, "Insulin History")
                    onDismissRequest()
                }) {
                    Text("Share Plain Text")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}
