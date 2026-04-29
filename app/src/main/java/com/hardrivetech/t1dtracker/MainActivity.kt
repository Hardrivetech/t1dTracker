package com.hardrivetech.t1dtracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.PrefsRepository
import com.hardrivetech.t1dtracker.ui.calculator.InsulinCalculatorScreen
import com.hardrivetech.t1dtracker.ui.history.HistoryScreen
import com.hardrivetech.t1dtracker.ui.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            T1DTrackerApp(prefs)
        }
    }
}

@Composable
fun T1DTrackerApp(prefs: PrefsRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "calculator"
    val context = LocalContext.current
    val biometricEnabled by prefs.biometricEnabled.collectAsState(initial = false)
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !isAuthenticated) {
            val result = BiometricAuth.authenticate(
                activity = context as FragmentActivity,
                title = context.getString(R.string.require_biometric),
                subtitle = "Authenticate to continue"
            )
            if (result) {
                isAuthenticated = true
            } else {
                // If auth fails, we might want to close the app or keep it locked
                (context as Activity).finish()
            }
        } else {
            isAuthenticated = true
        }
    }

    if (!isAuthenticated && biometricEnabled) {
        // Show a lock screen or just empty surface while authenticating
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            // Loading or lock icon
        }
        return
    }

    MaterialTheme {
        val primaryColor = MaterialTheme.colors.primary
        val useDarkIcons = primaryColor.luminance() > 0.5f
        val view = LocalView.current
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            window.statusBarColor = primaryColor.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = useDarkIcons
        }

        val topTitle = when (currentRoute) {
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
                        IconButton(onClick = {
                            if (currentRoute == "calculator") {
                                navController.navigate("history")
                            } else {
                                navController.navigate("calculator") {
                                    popUpTo("calculator") { inclusive = true }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (currentRoute == "calculator") Icons.Filled.ShowChart else Icons.Filled.Home,
                                contentDescription = stringResource(R.string.toggle_desc)
                            )
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_desc)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                T1DNavHost(navController)
            }
        }
    }
}

@Composable
fun T1DNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "calculator") {
        composable("calculator") { InsulinCalculatorScreen() }
        composable("history") { HistoryScreen() }
        composable("settings") { SettingsScreen { navController.popBackStack() } }
    }
}
