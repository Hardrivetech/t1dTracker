package com.hardrivetech.t1dtracker

import android.util.Base64
import javax.crypto.SecretKey

internal data class CombinedParts(val combined: ByteArray, val iv: ByteArray, val cipherBytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CombinedParts

        if (!combined.contentEquals(other.combined)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!cipherBytes.contentEquals(other.cipherBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = combined.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + cipherBytes.contentHashCode()
        return result
    }
}

internal fun splitCombinedParts(dataB64: String?, ivSize: Int = 12): CombinedParts? {
    if (dataB64 == null) return null
    return try {
        val combined = Base64.decode(dataB64, Base64.NO_WRAP)
        if (combined.size < ivSize) {
            AppLog.w("EncryptionUtil", "decryptAndDecodeBase64 failed (input too short)")
            TelemetryUtil.recordException(IllegalArgumentException("input too short"), "decryptAndDecodeBase64 failed")
            combined.fill(0)
            null
        } else {
            val iv = combined.copyOfRange(0, ivSize)
            val cipherBytes = combined.copyOfRange(ivSize, combined.size)
            CombinedParts(combined, iv, cipherBytes)
        }
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "decryptAndDecodeBase64 failed (invalid Base64): ${e.message}")
        TelemetryUtil.recordException(e, "decryptAndDecodeBase64 failed")
        null
    }
}

internal fun decryptCombined(secretKey: SecretKey, iv: ByteArray, cipherBytes: ByteArray, aesMode: String): ByteArray? {
    return try {
        val cipher = javax.crypto.Cipher.getInstance(aesMode)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
        cipher.doFinal(cipherBytes)
    } catch (e: java.security.GeneralSecurityException) {
        AppLog.w("EncryptionUtil", "decryptAndDecodeBase64 failed (crypto): ${e.message}")
        TelemetryUtil.recordException(e, "decryptAndDecodeBase64 failed")
        null
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "decryptAndDecodeBase64 failed (invalid data): ${e.message}")
        TelemetryUtil.recordException(e, "decryptAndDecodeBase64 failed")
        null
    }
}

internal fun decodeBase64OrNull(input: ByteArray?): ByteArray? {
    if (input == null) return null
    return try {
        Base64.decode(input, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        AppLog.w("EncryptionUtil", "Base64 decode failed in decryptAndDecodeBase64: ${e.message}")
        TelemetryUtil.recordException(e, "decryptAndDecodeBase64 Base64 decode failed")
        null
    }
}
