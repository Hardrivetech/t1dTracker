package com.hardrivetech.t1dtracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository

@Composable
fun SettingsScreen(db: AppDatabase, prefs: PrefsRepository, onNavigateBack: () -> Unit) {
    SettingsContent(db = db, prefs = prefs, onNavigateBack = onNavigateBack)
}

@Composable
private fun SettingsContent(db: AppDatabase, prefs: PrefsRepository, onNavigateBack: () -> Unit) {
    SettingsDefaultsSection(prefs = prefs, onNavigateBack = onNavigateBack)
    SettingsBackupMigrationSection(db = db, prefs = prefs)
}
