package com.hardrivetech.t1dtracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hardrivetech.t1dtracker.R
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository
import javax.inject.Inject

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        SettingsDefaultsSection(prefs = viewModel.getPrefsRepository(), onNavigateBack = onBack)
        
        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Backup & Migration",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SettingsBackupMigrationSection(db = viewModel.getDatabase(), prefs = viewModel.getPrefsRepository())
    }
}
