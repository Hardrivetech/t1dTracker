package com.hardrivetech.t1dtracker

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

object EncryptionUtil {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "t1d_t1dtracker_key_v1"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val WRAP_KEY_ALIAS = "t1d_t1dtracker_key_v1_wrap"

    private fun createSecretKey(context: Context): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)

            // Prefer StrongBox when available and supported by the platform
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                        builder.setIsStrongBoxBacked(true)
                    }
                } catch (_: Exception) {
                    // Ignore and continue without StrongBox
                }
            }

            // Require randomized encryption (keystore-generated IV) when available (API >= N)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { builder.setRandomizedEncryptionRequired(true) } catch (_: Exception) { }
            }

            val spec = builder.build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } else {
            // Pre-M fallback: generate AES key in app and return SecretKeySpec
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            return kg.generateKey()
        }
    }

    private fun getSecretKey(context: Context): SecretKey {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSecretKeyModern(context)
        } else {
            getSecretKeyLegacy(context)
        }
    }

    private fun getSecretKeyModern(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        return if (entry != null && entry is KeyStore.SecretKeyEntry) {
            entry.secretKey
        } else {
            createSecretKey(context)
        }
    }

    private fun getSecretKeyLegacy(context: Context): SecretKey {
        val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
        val wrappedB64 = prefs.getString("key_wrapped_b64", null)
        var resultKey: SecretKey? = null
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (wrappedB64 != null && keyStore.containsAlias(WRAP_KEY_ALIAS)) {
                resultKey = tryUnwrapWrappedKey(keyStore, wrappedB64)
            }

            if (resultKey == null) {
                ensureWrapKeyExists(context, keyStore)
                resultKey = generateAndWrapKeyIfPossible(keyStore, prefs, context)
            }
        } catch (e: Exception) {
            resultKey = generateTransientKey()
            AppLog.w(
                "EncryptionUtil",
                "Keystore not available; returning transient AES key (no persistent storage)"
            )
        }
        return resultKey!!
    }

    private fun tryUnwrapWrappedKey(keyStore: KeyStore, wrappedB64: String): SecretKey? {
        return try {
            val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
            val privateKey = keyStore.getKey(WRAP_KEY_ALIAS, null) as? java.security.PrivateKey
            if (privateKey != null) {
                val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                rsa.init(Cipher.DECRYPT_MODE, privateKey)
                val aesBytes = rsa.doFinal(wrapped)
                SecretKeySpec(aesBytes, "AES")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureWrapKeyExists(context: Context, keyStore: KeyStore) {
        try {
            if (!keyStore.containsAlias(WRAP_KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
                val start = Calendar.getInstance()
                val end = Calendar.getInstance()
                end.add(Calendar.YEAR, 30)
                val spec = KeyPairGeneratorSpec.Builder(context)
                    .setAlias(WRAP_KEY_ALIAS)
                    .setSubject(X500Principal("CN=$WRAP_KEY_ALIAS"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.time)
                    .setEndDate(end.time)
                    .build()
                kpg.initialize(spec)
                kpg.generateKeyPair()
            }
        } catch (_: Exception) {
            // If generating the wrap key fails, we'll fall back to transient key below.
        }
    }

    private fun generateAndWrapKeyIfPossible(
        keyStore: KeyStore,
        prefs: SharedPreferences,
        context: Context
    ): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        val key = kg.generateKey()
        try {
            val cert = keyStore.getCertificate(WRAP_KEY_ALIAS)
            if (cert != null) {
                val publicKey = cert.publicKey
                val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                rsa.init(Cipher.ENCRYPT_MODE, publicKey)
                val toWrap = key.encoded
                val wrapped = rsa.doFinal(toWrap)
                prefs.edit()
                    .putString("key_wrapped_b64", Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .apply()
                try { toWrap.fill(0) } catch (_: Exception) {}
                return key
            }
        } catch (_: Exception) {
            // wrapping failed; fall through to transient key return
        }

        return key
    }

    private fun generateTransientKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey()
    }

    fun encryptString(context: Context, plain: String): String {
        val key = getSecretKey(context)
        val cipher = Cipher.getInstance(AES_MODE)
        var iv: ByteArray? = null
        var inputBytes: ByteArray? = null
        var cipherBytes: ByteArray? = null
        try {
            // If the key is provider-backed by AndroidKeyStore, the keystore will generate the IV.
            // Keystore-backed secret keys return null from `encoded`, so detect that and avoid
            // supplying a caller-provided IV (some keystores forbid it).
            if (key.encoded == null) {
                cipher.init(Cipher.ENCRYPT_MODE, key)
                iv = cipher.iv
            } else {
                iv = ByteArray(IV_SIZE)
                SecureRandom().nextBytes(iv)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            }

            inputBytes = plain.toByteArray(Charsets.UTF_8)
            cipherBytes = cipher.doFinal(inputBytes)
            val combined = ByteArray(iv!!.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } finally {
            // Zero sensitive temporary buffers (best-effort)
            try { inputBytes?.fill(0) } catch (_: Exception) {}
            try { iv?.fill(0) } catch (_: Exception) {}
            try { cipherBytes?.fill(0) } catch (_: Exception) {}
        }
    }

    fun decryptString(context: Context, dataB64: String?): String? {
        if (dataB64 == null) return null
        var combined: ByteArray? = null
        var iv: ByteArray? = null
        var cipherBytes: ByteArray? = null
        var plain: ByteArray? = null
        try {
            combined = Base64.decode(dataB64, Base64.NO_WRAP)
            if (combined.size < IV_SIZE) return null
            iv = combined.copyOfRange(0, IV_SIZE)
            cipherBytes = combined.copyOfRange(IV_SIZE, combined.size)
            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            plain = cipher.doFinal(cipherBytes)
            return String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        } finally {
            try { iv?.fill(0) } catch (_: Exception) {}
            try { cipherBytes?.fill(0) } catch (_: Exception) {}
            try { plain?.fill(0) } catch (_: Exception) {}
            try { combined?.fill(0) } catch (_: Exception) {}
        }
    }

    /**
     * Decrypts `dataB64` (the value produced by `encryptString`) and then Base64-decodes
     * the plaintext, returning the raw bytes. Caller MUST zero the returned byte[] when done.
     */
    fun decryptAndDecodeBase64(context: Context, dataB64: String?): ByteArray? {
        if (dataB64 == null) return null
        var combined: ByteArray? = null
        var iv: ByteArray? = null
        var cipherBytes: ByteArray? = null
        var plain: ByteArray? = null
        try {
            combined = Base64.decode(dataB64, Base64.NO_WRAP)
            if (combined.size < IV_SIZE) return null
            iv = combined.copyOfRange(0, IV_SIZE)
            cipherBytes = combined.copyOfRange(IV_SIZE, combined.size)
            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            plain = cipher.doFinal(cipherBytes)
            // plain contains the Base64-encoded passphrase; decode it to raw bytes
            val decoded = try {
                Base64.decode(plain, Base64.NO_WRAP)
            } catch (_: Exception) {
                null
            }

            return decoded
        } catch (_: Exception) {
            return null
        } finally {
            try { iv?.fill(0) } catch (_: Exception) {}
            try { cipherBytes?.fill(0) } catch (_: Exception) {}
            try { plain?.fill(0) } catch (_: Exception) {}
            try { combined?.fill(0) } catch (_: Exception) {}
        }
    }

    /**
     * Returns true when the platform keystore can be used to securely persist keys for
     * encrypting the database/passphrases. This is true on API>=M, or on older devices
     * when an RSA wrap key can be created in AndroidKeyStore for wrapping AES keys.
     */
    fun isKeystoreUsable(context: Context): Boolean {
        try {
            // Ensure the AndroidKeyStore can be loaded and accessed.
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // On modern platforms (API >= M) loading the AndroidKeyStore successfully
            // is a reasonable indicator that keystore operations will work.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return true

            // For pre-M devices, check for an existing wrap key or attempt to create one.
            if (keyStore.containsAlias(WRAP_KEY_ALIAS)) return true

            // Try to create the wrap key for pre-M devices
            val kpg = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
            val start = Calendar.getInstance()
            val end = Calendar.getInstance()
            end.add(Calendar.YEAR, 30)
            val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(WRAP_KEY_ALIAS)
                .setSubject(X500Principal("CN=$WRAP_KEY_ALIAS"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.time)
                .setEndDate(end.time)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
            return true
        } catch (e: Exception) {
            // Keystore not usable on this device or inaccessible in the current context.
            AppLog.w("EncryptionUtil", "Keystore unusable: ${e.message}")
            try { TelemetryUtil.recordException(e, "isKeystoreUsable failed") } catch (_: Exception) { }
            return false
        }
    }

    /**
     * Rotate the keystore-backed AES key (same alias). This will:
     *  - decrypt the current `db_pass_enc` value,
     *  - delete the existing keystore entry for `KEY_ALIAS` (if present),
     *  - generate a new key under `KEY_ALIAS`, and
     *  - re-encrypt and persist the `db_pass_enc` value with the new key.
     *
     * Returns true on success. This is best-effort and will log failures.
     */
    fun rotateKey(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
            val enc = prefs.getString("db_pass_enc", null) ?: return false

            // Decrypt to raw passphrase bytes (not a String) so we can zero them safely
            val passphraseBytes = decryptAndDecodeBase64(context, enc) ?: return false

            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    try { keyStore.deleteEntry(KEY_ALIAS) } catch (_: Exception) { }
                }
            } catch (_: Exception) {
                // If keystore not available, continue; createSecretKey will handle fallbacks
            }

            // Force creation of a fresh key under the same alias
            try { createSecretKey(context) } catch (_: Exception) { }

            // Re-encrypt the passphrase (Base64-encode raw bytes first)
            val passphraseBase64 = Base64.encodeToString(passphraseBytes, Base64.NO_WRAP)
            val newEnc = encryptString(context, passphraseBase64)
            prefs.edit().putString("db_pass_enc", newEnc).apply()

            // Zero sensitive buffers
            try { passphraseBytes.fill(0) } catch (_: Exception) { }
            try { passphraseBase64.toByteArray(Charsets.UTF_8).fill(0) } catch (_: Exception) { }

            AppLog.i("EncryptionUtil", "Keystore key rotated for alias $KEY_ALIAS")
            return true
        } catch (e: Exception) {
            AppLog.e("EncryptionUtil", "rotateKey failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "EncryptionUtil.rotateKey failed")
            return false
        }
    }
}
