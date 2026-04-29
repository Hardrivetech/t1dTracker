package com.hardrivetech.t1dtracker

import android.util.Log

object AppLog {
    private val isDebug: Boolean by lazy {
        try {
            val cls = Class.forName("com.hardrivetech.t1dtracker.BuildConfig")
            val f = cls.getField("DEBUG")
            f.getBoolean(null)
        } catch (e: ReflectiveOperationException) {
            Log.e("AppLog", "AppLog.isDebug detection failed", e)
            false
        } catch (e: SecurityException) {
            Log.e("AppLog", "AppLog.isDebug detection failed", e)
            false
        }
    }

    fun i(tag: String, msg: String?) {
        if (isDebug) Log.i(tag, PrivacyUtil.redactPII(msg) ?: "")
    }

    fun d(tag: String, msg: String?) {
        if (isDebug) Log.d(tag, PrivacyUtil.redactPII(msg) ?: "")
    }

    fun w(tag: String, msg: String?) {
        if (isDebug) Log.w(tag, PrivacyUtil.redactPII(msg) ?: "")
    }

    fun e(tag: String, msg: String?, t: Throwable? = null) {
        if (isDebug) Log.e(tag, PrivacyUtil.redactPII(msg) ?: "", t)
    }
}
