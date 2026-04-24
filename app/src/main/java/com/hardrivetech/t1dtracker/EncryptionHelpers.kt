package com.hardrivetech.t1dtracker

import android.content.Context
import android.content.SharedPreferences
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import androidx.core.content.edit
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

// Helper functions extracted from EncryptionUtil to reduce object size and complexity
internal fun getSecretKeyLegacy(context: Context): SecretKey {
    val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
    val wrappedB64 = prefs.getString("key_wrapped_b64", null)
    var resultKey: SecretKey? = null
    try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (wrappedB64 != null && keyStore.containsAlias("t1d_t1dtracker_key_v1_wrap")) {
            resultKey = tryUnwrapWrappedKey(keyStore, wrappedB64)
        }

        if (resultKey == null) {
            ensureWrapKeyExists(context, keyStore)
            resultKey = generateAndWrapKeyIfPossible(keyStore, prefs, context)
        }
    } catch (e: GeneralSecurityException) {
        resultKey = generateTransientKey()
        AppLog.w(
            "EncryptionUtil",
            "Keystore not available; returning transient AES key (no persistent storage)"
        )
        TelemetryUtil.recordException(e, "getSecretKeyLegacy fallback to transient key")
    } catch (e: IOException) {
        resultKey = generateTransientKey()
        AppLog.w(
            "EncryptionUtil",
            "Keystore I/O failure; returning transient AES key (no persistent storage)"
        )
        TelemetryUtil.recordException(e, "getSecretKeyLegacy fallback to transient key")
    }
    return resultKey
}

internal fun tryUnwrapWrappedKey(keyStore: KeyStore, wrappedB64: String): SecretKey? {
    return try {
        val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
        val privateKey = keyStore.getKey("t1d_t1dtracker_key_v1_wrap", null) as? java.security.PrivateKey
        if (privateKey != null) {
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.DECRYPT_MODE, privateKey)
            val aesBytes = rsa.doFinal(wrapped)
            SecretKeySpec(aesBytes, "AES")
        } else {
            null
        }
    } catch (e: GeneralSecurityException) {
        AppLog.w("EncryptionUtil", "Unwrap wrapped key failed (crypto): ${e.message}")
        TelemetryUtil.recordException(e, "tryUnwrapWrappedKey failed")
        null
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "Unwrap wrapped key failed (invalid data): ${e.message}")
        TelemetryUtil.recordException(e, "tryUnwrapWrappedKey failed")
        null
    }
}

internal fun ensureWrapKeyExists(context: Context, keyStore: KeyStore) {
    try {
        if (!keyStore.containsAlias("t1d_t1dtracker_key_v1_wrap")) {
            val kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            val start = Calendar.getInstance()
            val end = Calendar.getInstance()
            end.add(Calendar.YEAR, 30)
            val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias("t1d_t1dtracker_key_v1_wrap")
                .setSubject(X500Principal("CN=t1d_t1dtracker_key_v1_wrap"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.time)
                .setEndDate(end.time)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
    } catch (e: GeneralSecurityException) {
        AppLog.w("EncryptionUtil", "ensureWrapKeyExists failed: ${e.message}")
        TelemetryUtil.recordException(e, "ensureWrapKeyExists failed")
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "ensureWrapKeyExists failed: ${e.message}")
        TelemetryUtil.recordException(e, "ensureWrapKeyExists failed")
    }
}

internal fun generateAndWrapKeyIfPossible(
    keyStore: KeyStore,
    prefs: SharedPreferences,
    context: Context
): SecretKey {
    // Use `context` to avoid unused-parameter warnings while keeping signature stable for callers.
    val _ = context.applicationContext
    val kg = KeyGenerator.getInstance("AES")
    kg.init(256)
    val key = kg.generateKey()
    try {
        val cert = keyStore.getCertificate("t1d_t1dtracker_key_v1_wrap")
        if (cert != null) {
            val publicKey = cert.publicKey
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, publicKey)
            val toWrap = key.encoded
            val wrapped = rsa.doFinal(toWrap)
            prefs.edit {
                putString("key_wrapped_b64", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            }
            if (toWrap != null) {
                toWrap.fill(0)
            }
            return key
        }
    } catch (e: GeneralSecurityException) {
        AppLog.w("EncryptionUtil", "generateAndWrapKeyIfPossible wrapping failed (crypto): ${e.message}")
        TelemetryUtil.recordException(e, "generateAndWrapKeyIfPossible failed")
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "generateAndWrapKeyIfPossible wrapping failed (invalid data): ${e.message}")
        TelemetryUtil.recordException(e, "generateAndWrapKeyIfPossible failed")
    }

    return key
}

internal fun generateTransientKey(): SecretKey {
    val kg = KeyGenerator.getInstance("AES")
    kg.init(256)
    return kg.generateKey()
}
