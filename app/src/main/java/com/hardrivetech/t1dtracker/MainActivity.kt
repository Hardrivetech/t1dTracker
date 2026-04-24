package com.hardrivetech.t1dtracker

import android.os.Bundle
import android.app.Activity
import android.widget.Toast
import android.graphics.Color as AndroidColor
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.hardrivetech.t1dtracker.util.PasswordStrength
import com.hardrivetech.t1dtracker.data.BackupUtil
import com.hardrivetech.t1dtracker.data.BackupImporter
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.data.listMigrationBackups
import com.hardrivetech.t1dtracker.data.restoreMigrationBackup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the system status bar matches the app purple color
        window.statusBarColor = AndroidColor.parseColor("#6200EE")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val db = AppDatabase.getInstance(applicationContext)
        val prefs = PrefsRepository(applicationContext)
        setContent {
            T1DTrackerApp(db, prefs)
        }
    }
}

@Composable
fun T1DTrackerApp(db: AppDatabase, prefs: PrefsRepository) {
    var screen by remember { mutableStateOf("calculator") }
    val telemetryConsent by prefs.telemetryConsent.collectAsState(initial = false)
    val biometricEnabled by prefs.biometricEnabled.collectAsState(initial = false)
    val appContext = LocalContext.current
    LaunchedEffect(telemetryConsent) {
        TelemetryUtil.setTelemetryEnabled(appContext, telemetryConsent)
    }
    val activity = LocalContext.current as? FragmentActivity
    val appScope = rememberCoroutineScope()
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) {
            if (activity == null) {
                appScope.launch { prefs.setBiometricEnabled(false) }
            } else {
                if (!BiometricAuth.isAvailable(appContext)) {
                    Toast.makeText(appContext, appContext.getString(R.string.biometric_unavailable_disabling), Toast.LENGTH_SHORT).show()
                    appScope.launch { prefs.setBiometricEnabled(false) }
                } else {
                    val ok = BiometricAuth.authenticate(activity, "Unlock t1dTracker", "Authenticate to continue")
                    if (!ok) {
                        activity.finish()
                    }
                }
            }
        }
    }

    MaterialTheme {
        val primaryColor = MaterialTheme.colors.primary
        val useDarkIcons = primaryColor.luminance() > 0.5f
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = primaryColor.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = useDarkIcons
            }
        }
        val topTitle = when (screen) {
            "calculator" -> stringResource(R.string.calculator_title)
            "history" -> stringResource(R.string.history_title)
            "settings" -> stringResource(R.string.default_settings_title)
            else -> stringResource(R.string.app_name)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(topTitle) },
                    backgroundColor = primaryColor,
                    contentColor = MaterialTheme.colors.onPrimary,
                    actions = {
                        IconButton(onClick = { screen = if (screen == "calculator") "history" else "calculator" }) {
                            Icon(
                                imageVector = if (screen == "calculator") Icons.Filled.ShowChart else Icons.Filled.Home,
                                contentDescription = stringResource(R.string.toggle_desc)
                            )
                        }
                        IconButton(onClick = { screen = "settings" }) {
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_desc))
                        }
                    }
                )
            }
        ) { padding ->
            Surface(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                when (screen) {
                    "calculator" -> InsulinCalculatorScreen(db, prefs)
                    "history" -> HistoryScreen(db)
                    "settings" -> SettingsScreen(db, prefs) { screen = "calculator" }
                    else -> InsulinCalculatorScreen(db, prefs)
                }
            }
        }
    }
}

@Composable
fun InsulinCalculatorScreen(db: AppDatabase, prefs: PrefsRepository) {
    var carbsText by remember { mutableStateOf("") }
    var icrText by remember { mutableStateOf("") }
    var currentText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var isfText by remember { mutableStateOf("") }
    var roundingText by remember { mutableStateOf("0.5") }

    val defaultICR by prefs.defaultICR.collectAsState(initial = 0.0)
    val defaultISF by prefs.defaultISF.collectAsState(initial = 0.0)
    val defaultTarget by prefs.defaultTarget.collectAsState(initial = 0.0)

    LaunchedEffect(defaultICR) { if (icrText.isEmpty() && defaultICR > 0.0) icrText = defaultICR.toString() }
    LaunchedEffect(defaultISF) { if (isfText.isEmpty() && defaultISF > 0.0) isfText = defaultISF.toString() }
    LaunchedEffect(defaultTarget) { if (targetText.isEmpty() && defaultTarget > 0.0) targetText = defaultTarget.toString() }

    val carbs = carbsText.toDoubleOrNull() ?: 0.0
    val icrEntered = icrText.toDoubleOrNull() ?: 0.0
    val icr = if (icrEntered > 0.0) icrEntered else defaultICR
    val currentGlucose = currentText.toDoubleOrNull() ?: 0.0
    val targetGlucose = targetText.toDoubleOrNull() ?: 0.0
    val isfEntered = isfText.toDoubleOrNull() ?: 0.0
    val isf = if (isfEntered > 0.0) isfEntered else defaultISF
    val rounding = roundingText.toDoubleOrNull() ?: 0.5

    val carbDose = if (icr > 0) carbs / icr else 0.0
    val correctionDose = if (isf > 0 && currentGlucose > targetGlucose) (currentGlucose - targetGlucose) / isf else 0.0
    val totalDoseRaw = carbDose + correctionDose
    val totalDose = (kotlin.math.round(totalDoseRaw / rounding) * rounding)

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var entries by remember { mutableStateOf(listOf<InsulinEntry>()) }

    LaunchedEffect(db) { entries = db.insulinDao().getAll() }

    Column(modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        NumberField(stringResource(R.string.carbs_label), carbsText) { carbsText = it }
        NumberField(stringResource(R.string.icr_label), icrText) { icrText = it }
        Spacer(modifier = Modifier.height(8.dp))
        NumberField(stringResource(R.string.current_glucose_label), currentText) { currentText = it }
        NumberField(stringResource(R.string.target_glucose_label), targetText) { targetText = it }
        NumberField(stringResource(R.string.isf_label), isfText) { isfText = it }
        NumberField(stringResource(R.string.rounding_label), roundingText) { roundingText = it }

        Spacer(modifier = Modifier.height(16.dp))
        Text("${stringResource(R.string.carb_dose_label)}: ${formatDose(carbDose)} U")
        Text("${stringResource(R.string.correction_dose_label)}: ${formatDose(correctionDose)} U")
        Text("Total dose (rounded ${rounding}U): ${formatDose(totalDose)} U", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                val entry = InsulinEntry(
                    timestamp = System.currentTimeMillis(),
                    carbs = carbs,
                    icr = icr,
                    currentGlucose = currentGlucose,
                    targetGlucose = targetGlucose,
                    isf = isf,
                    carbDose = carbDose,
                    correctionDose = correctionDose,
                    totalDose = totalDose
                )
                db.insulinDao().insert(entry)
                entries = db.insulinDao().getAll()
                Toast.makeText(context, context.getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
            }
        }) { Text(stringResource(R.string.save_entry)) }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.recent_entries), style = MaterialTheme.typography.subtitle1)
        Spacer(modifier = Modifier.height(8.dp))
        for (e in entries) {
            Text("${formatTime(e.timestamp)} — ${formatDose(e.totalDose)} U — ${e.carbs} g")
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
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

fun formatDose(value: Double): String {
    return if (value % 1.0 == 0.0) "%.0f".format(value) else "%.2f".format(value)
}

fun formatTime(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
