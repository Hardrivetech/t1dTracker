package com.hardrivetech.t1dtracker.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.EncryptionUtil
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.security.GeneralSecurityException
import java.security.SecureRandom

internal fun persistNewPassphrase(context: Context): ByteArray? {
    return try {
        val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val passphraseBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val enc = EncryptionUtil.encryptString(context, passphraseBase64)
        prefs.edit { putString("db_pass_enc", enc) }
        bytes
    } catch (e: GeneralSecurityException) {
        AppLog.e("AppDatabase", "Failed to generate/persist DB passphrase: ${e.message}", e)
        TelemetryUtil.recordException(e, "migrate: passphrase generation failed")
        null
    }
}
