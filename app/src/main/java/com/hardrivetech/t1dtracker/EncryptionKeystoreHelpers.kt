package com.hardrivetech.t1dtracker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import java.io.IOException
import java.security.GeneralSecurityException

internal fun tryApplyStrongBox(builder: KeyGenParameterSpec.Builder, context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                builder.setIsStrongBoxBacked(true)
            }
        } catch (se: SecurityException) {
            AppLog.w("EncryptionUtil", "StrongBox feature check failed: ${se.message}")
            TelemetryUtil.recordException(se, "createSecretKey StrongBox feature check failed")
        }
    }
}

internal fun tryRequireRandomizedEncryption(builder: KeyGenParameterSpec.Builder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            builder.setRandomizedEncryptionRequired(true)
        } catch (se: SecurityException) {
            AppLog.w("EncryptionUtil", "Randomized encryption requirement not supported: ${se.message}")
            TelemetryUtil.recordException(se, "createSecretKey randomized encryption requirement failed")
        }
    }
}

internal fun tryDeleteKeystoreEntry(alias: String) {
    try {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            try {
                keyStore.deleteEntry(alias)
            } catch (ke: GeneralSecurityException) {
                AppLog.w("EncryptionUtil", "Failed to delete keystore entry: ${ke.message}")
                TelemetryUtil.recordException(ke, "tryDeleteKeystoreEntry failed")
            }
        }
    } catch (e: GeneralSecurityException) {
        AppLog.w("EncryptionUtil", "Keystore access failed during delete: ${e.message}")
        TelemetryUtil.recordException(e, "tryDeleteKeystoreEntry failed")
    } catch (e: IOException) {
        AppLog.w("EncryptionUtil", "Keystore I/O failed during delete: ${e.message}")
        TelemetryUtil.recordException(e, "tryDeleteKeystoreEntry failed")
    }
}
