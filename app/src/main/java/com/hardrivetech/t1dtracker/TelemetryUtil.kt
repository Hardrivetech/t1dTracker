package com.hardrivetech.t1dtracker

import android.content.Context

object TelemetryUtil {
    @Volatile
    private var telemetryEnabled: Boolean = false

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        telemetryEnabled = enabled
        try {
            val crashCls = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstance = crashCls.getMethod("getInstance")
            val crashInstance = getInstance.invoke(null)
            val setEnabled = crashCls.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
            setEnabled.invoke(crashInstance, enabled)
        } catch (t: Throwable) {
            AppLog.i("TelemetryUtil", "Crashlytics not available: ${t.message}")
        }

        try {
            val analyticsCls = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val getInstance = analyticsCls.getMethod("getInstance", Context::class.java)
            val analyticsInstance = getInstance.invoke(null, context)
            val setEnabled = analyticsInstance.javaClass.getMethod(
                "setAnalyticsCollectionEnabled",
                Boolean::class.javaPrimitiveType
            )
            setEnabled.invoke(analyticsInstance, enabled)
        } catch (t: Throwable) {
            AppLog.i("TelemetryUtil", "Firebase Analytics not available: ${t.message}")
        }
    }

    fun log(message: String) {
        val redacted = PrivacyUtil.redactPII(message) ?: message
        if (!telemetryEnabled) {
            AppLog.i("TelemetryUtil", "Telemetry disabled; log: $redacted")
            return
        }
        try {
            val crashCls = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstance = crashCls.getMethod("getInstance")
            val crashInstance = getInstance.invoke(null)
            val logMethod = crashCls.getMethod("log", String::class.java)
            logMethod.invoke(crashInstance, redacted)
        } catch (t: Throwable) {
            AppLog.i("TelemetryUtil", "Crashlytics log not available: ${t.message}")
        }
    }

    fun recordException(t: Throwable, message: String? = null) {
        val sanitizedMsg = PrivacyUtil.redactPII(message ?: t.message ?: t.toString()) ?: (message ?: t::class.java.name)
        if (!telemetryEnabled) {
            AppLog.e("TelemetryUtil", "Telemetry disabled; exception: $sanitizedMsg", t)
            return
        }
        try {
            val crashCls = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstance = crashCls.getMethod("getInstance")
            val crashInstance = getInstance.invoke(null)
            val record = crashCls.getMethod("recordException", Throwable::class.java)
            val sanitizedThrowable = Throwable(sanitizedMsg)
            sanitizedThrowable.stackTrace = t.stackTrace
            record.invoke(crashInstance, sanitizedThrowable)
            // also log the sanitized message
            val logMethod = crashCls.getMethod("log", String::class.java)
            logMethod.invoke(crashInstance, sanitizedMsg)
        } catch (e: Throwable) {
            AppLog.e("TelemetryUtil", "Crashlytics recordException failed: ${e.message}", e)
        }
    }
}
