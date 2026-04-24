package com.hardrivetech.t1dtracker

import android.app.Application
import android.util.Log

class T1DApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase / Crashlytics if available (guarded via reflection)
        try {
            val firebaseApp = Class.forName("com.google.firebase.FirebaseApp")
            val initializeApp = firebaseApp.getMethod("initializeApp", android.content.Context::class.java)
            val app = initializeApp.invoke(null, this)
            if (app != null) {
                try {
                    val crashCls = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
                    val getInstance = crashCls.getMethod("getInstance")
                    val crashInstance = getInstance.invoke(null)
                    val setEnabled = crashCls.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
                    // Default to disabled; enable only after explicit user consent.
                    setEnabled.invoke(crashInstance, false)
                } catch (t: Throwable) {
                    AppLog.i("T1DApplication", "Crashlytics not available: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            AppLog.i("T1DApplication", "Firebase not configured: ${t.message}")
        }
    }
}
