package com.hardrivetech.t1dtracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class T1DApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase if it's available in the classpath
        // The check is still useful because build.gradle only applies the plugin if google-services.json exists
        try {
            FirebaseApp.initializeApp(this)
            // Default to disabled; enable only after explicit user consent if needed.
            // For now, we follow the previous logic of keeping it disabled initially.
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        } catch (e: Exception) {
            AppLog.i("T1DApplication", "Firebase not initialized: ${e.message}")
        }
    }
}
