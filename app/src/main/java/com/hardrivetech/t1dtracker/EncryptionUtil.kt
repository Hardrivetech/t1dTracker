package com.hardrivetech.t1dtracker

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.security.auth.x500.X500Principal

object EncryptionUtil {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "t1d_t1dtracker_key_v1"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val WRAP_KEY_ALIAS = "t1d_t1dtracker_key_v1_wrap"

    // Keystore helper functions (tryApplyStrongBox, tryRequireRandomizedEncryption,
    // tryDeleteKeystoreEntry) were extracted to EncryptionKeystoreHelpers.kt to
    // reduce the number of functions inside this object for static analysis.

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

            // Prefer StrongBox and require randomized encryption when supported.
            tryApplyStrongBox(builder, context)
            tryRequireRandomizedEncryption(builder)

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
        var result: SecretKey? = null
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null)
            result = if (entry != null && entry is KeyStore.SecretKeyEntry) {
                entry.secretKey
            } else {
                createSecretKey(context)
            }
        } catch (e: GeneralSecurityException) {
            AppLog.w("EncryptionUtil", "Keystore access failed (modern): ${e.message}")
            TelemetryUtil.recordException(e, "getSecretKeyModern failed")
            result = createSecretKey(context)
        } catch (e: IOException) {
            AppLog.w("EncryptionUtil", "Keystore I/O failed (modern): ${e.message}")
            TelemetryUtil.recordException(e, "getSecretKeyModern failed")
            result = createSecretKey(context)
        }

        return result!!
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
            inputBytes?.fill(0)
            iv?.fill(0)
            cipherBytes?.fill(0)
        }
    }

    fun decryptString(context: Context, dataB64: String?): String? {
        var result: String? = null
        var combined: ByteArray? = null
        var iv: ByteArray? = null
        var cipherBytes: ByteArray? = null
        var plain: ByteArray? = null
        try {
            if (dataB64 != null) {
                combined = Base64.decode(dataB64, Base64.NO_WRAP)
                if (combined.size >= IV_SIZE) {
                    iv = combined.copyOfRange(0, IV_SIZE)
                    cipherBytes = combined.copyOfRange(IV_SIZE, combined.size)
                    val key = getSecretKey(context)
                    val cipher = Cipher.getInstance(AES_MODE)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, key, spec)
                    plain = cipher.doFinal(cipherBytes)
                    result = String(plain, Charsets.UTF_8)
                } else {
                    AppLog.w("EncryptionUtil", "decryptString failed (input too short)")
                    TelemetryUtil.recordException(IllegalArgumentException("input too short"), "decryptString failed")
                }
            }
        } catch (e: GeneralSecurityException) {
            AppLog.w("EncryptionUtil", "decryptString failed (crypto): ${e.message}")
            TelemetryUtil.recordException(e, "decryptString failed")
        } catch (e: IllegalArgumentException) {
            AppLog.w("EncryptionUtil", "decryptString failed (invalid data): ${e.message}")
            TelemetryUtil.recordException(e, "decryptString failed")
        } finally {
            iv?.fill(0)
            cipherBytes?.fill(0)
            plain?.fill(0)
            combined?.fill(0)
        }

        return result
    }

    /**
     * Decrypts `dataB64` (the value produced by `encryptString`) and then Base64-decodes
     * the plaintext, returning the raw bytes. Caller MUST zero the returned byte[] when done.
     */
    fun decryptAndDecodeBase64(context: Context, dataB64: String?): ByteArray? {
        var result: ByteArray? = null
        var parts: CombinedParts? = null
        var plain: ByteArray? = null
        try {
            parts = splitCombinedParts(dataB64)
            if (parts != null) {
                val key = getSecretKey(context)
                plain = decryptCombined(key, parts.iv, parts.cipherBytes, AES_MODE)
                result = decodeBase64OrNull(plain)
            }
        } finally {
            parts?.iv?.fill(0)
            parts?.cipherBytes?.fill(0)
            parts?.combined?.fill(0)
            plain?.fill(0)
        }

        return result
    }

    /**
     * Returns true when the platform keystore can be used to securely persist keys for
     * encrypting the database/passphrases. This is true on API>=M, or on older devices
     * when an RSA wrap key can be created in AndroidKeyStore for wrapping AES keys.
     */
    fun isKeystoreUsable(context: Context): Boolean {
        var usable = false
        try {
            // Ensure the AndroidKeyStore can be loaded and accessed.
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // On modern platforms (API >= M) loading the AndroidKeyStore successfully
            // is a reasonable indicator that keystore operations will work.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                usable = true
            } else {
                // For pre-M devices, check for an existing wrap key or attempt to create one.
                if (keyStore.containsAlias(WRAP_KEY_ALIAS)) {
                    usable = true
                } else {
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
                    usable = true
                }
            }
        } catch (e: GeneralSecurityException) {
            // Keystore not usable on this device or inaccessible in the current context.
            AppLog.w("EncryptionUtil", "Keystore unusable: ${e.message}")
            TelemetryUtil.recordException(e, "isKeystoreUsable failed")
            usable = false
        } catch (e: IOException) {
            AppLog.w("EncryptionUtil", "Keystore I/O failure: ${e.message}")
            TelemetryUtil.recordException(e, "isKeystoreUsable failed")
            usable = false
        }

        return usable
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
        var success = false
        try {
            val prefs = context.getSharedPreferences("t1d_crypto", Context.MODE_PRIVATE)
            val enc = prefs.getString("db_pass_enc", null)
                ?: error("db_pass_enc missing; nothing to rotate")

            // Decrypt to raw passphrase bytes (not a String) so we can zero them safely
            val passphraseBytes = decryptAndDecodeBase64(context, enc)
                ?: throw GeneralSecurityException("decryptAndDecodeBase64 returned null")

            // Attempt to remove any existing key entry; helper handles logging/telemetry.
            tryDeleteKeystoreEntry(KEY_ALIAS)

            // Force creation of a fresh key under the same alias
            try {
                createSecretKey(context)
            } catch (e: GeneralSecurityException) {
                AppLog.w("EncryptionUtil", "createSecretKey during rotate failed: ${e.message}")
                TelemetryUtil.recordException(e, "rotateKey createSecretKey failed")
            }

            // Re-encrypt the passphrase (Base64-encode raw bytes first)
            val passphraseBase64 = Base64.encodeToString(passphraseBytes, Base64.NO_WRAP)
            val newEnc = encryptString(context, passphraseBase64)
            prefs.edit().putString("db_pass_enc", newEnc).apply()

            // Zero sensitive buffers
            passphraseBytes.fill(0)
            passphraseBase64.toByteArray(Charsets.UTF_8).fill(0)

            AppLog.i("EncryptionUtil", "Keystore key rotated for alias $KEY_ALIAS")
            success = true
        } catch (e: IllegalStateException) {
            AppLog.w("EncryptionUtil", "rotateKey precondition failed: ${e.message}")
            TelemetryUtil.recordException(e, "rotateKey precondition failed")
        } catch (e: GeneralSecurityException) {
            AppLog.e("EncryptionUtil", "rotateKey failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "EncryptionUtil.rotateKey failed")
        } catch (e: IOException) {
            AppLog.e("EncryptionUtil", "rotateKey I/O failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "EncryptionUtil.rotateKey failed")
        }

        return success
    }
}
