package com.hardrivetech.t1dtracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository

@Composable
fun SettingsScreen(db: AppDatabase, prefs: PrefsRepository, onNavigateBack: () -> Unit) {
    SettingsContent(db = db, prefs = prefs, onNavigateBack = onNavigateBack)
}

@Composable
private fun SettingsContent(db: AppDatabase, prefs: PrefsRepository, onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsDefaultsSection(prefs = prefs, onNavigateBack = onNavigateBack)
        SettingsBackupMigrationSection(db = db, prefs = prefs)
    }
}
