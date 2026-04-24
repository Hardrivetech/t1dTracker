package com.hardrivetech.t1dtracker.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

object BackupUtil {
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    // Increased iterations for PBKDF2 to harden against offline attacks.
    // Updated to 400k in 2026 to strengthen against offline brute-force.
    private const val PBKDF2_ITERATIONS = 400_000
    private val MAGIC = "T1D1".toByteArray(Charsets.US_ASCII)

    fun buildJsonBackup(entries: List<InsulinEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("timestamp", e.timestamp)
            obj.put("carbs", e.carbs)
            obj.put("icr", e.icr)
            obj.put("currentGlucose", e.currentGlucose)
            obj.put("targetGlucose", e.targetGlucose)
            obj.put("isf", e.isf)
            obj.put("carbDose", e.carbDose)
            obj.put("correctionDose", e.correctionDose)
            obj.put("totalDose", e.totalDose)
            obj.put("notes", e.notes)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("formatVersion", 1)
        root.put("createdAt", System.currentTimeMillis())
        root.put("entries", arr)
        return root.toString()
    }

    fun encryptBackupWithPassword(password: CharArray, plaintext: ByteArray, iterations: Int = PBKDF2_ITERATIONS): String {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val tmp = factory.generateSecret(spec)
            val tmpBytes = tmp.encoded
            try {
                val key = SecretKeySpec(tmpBytes, "AES")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(IV_SIZE)
                SecureRandom().nextBytes(iv)
                val gcm = GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, gcm)

                val cipherBytes = cipher.doFinal(plaintext)

                // Format: MAGIC(4) | iterations(4 big-endian) | salt | iv | ciphertext
                val iterBytes = byteArrayOf(
                    ((iterations ushr 24) and 0xFF).toByte(),
                    ((iterations ushr 16) and 0xFF).toByte(),
                    ((iterations ushr 8) and 0xFF).toByte(),
                    (iterations and 0xFF).toByte()
                )

                val combined = ByteArray(MAGIC.size + iterBytes.size + salt.size + iv.size + cipherBytes.size)
                var offset = 0
                System.arraycopy(MAGIC, 0, combined, offset, MAGIC.size); offset += MAGIC.size
                System.arraycopy(iterBytes, 0, combined, offset, iterBytes.size); offset += iterBytes.size
                System.arraycopy(salt, 0, combined, offset, salt.size); offset += salt.size
                System.arraycopy(iv, 0, combined, offset, iv.size); offset += iv.size
                System.arraycopy(cipherBytes, 0, combined, offset, cipherBytes.size)

                val out = Base64.encodeToString(combined, Base64.NO_WRAP)

                try { iv.fill(0) } catch (_: Exception) { }
                try { cipherBytes.fill(0) } catch (_: Exception) { }

                return out
            } finally {
                if (tmpBytes != null) try { tmpBytes.fill(0) } catch (_: Exception) { }
            }
        } finally {
            // Clear password material from PBEKeySpec
            try { spec.clearPassword() } catch (_: Exception) { }
        }
    }

    fun decryptBackupWithPassword(password: CharArray, combinedB64: String, iterationsDefault: Int = PBKDF2_ITERATIONS): ByteArray? {
        return try {
            val combined = Base64.decode(combinedB64, Base64.NO_WRAP)
            if (combined.size < SALT_SIZE + IV_SIZE) return null

            var offset = 0
            var iterations = iterationsDefault
            val salt: ByteArray
            val iv: ByteArray
            val cipherBytes: ByteArray

            // Check for new format with MAGIC prefix
            if (combined.size >= MAGIC.size && combined.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                offset += MAGIC.size
                if (combined.size < offset + 4 + SALT_SIZE + IV_SIZE) return null
                val iterBytes = combined.copyOfRange(offset, offset + 4); offset += 4
                iterations = ((iterBytes[0].toInt() and 0xFF) shl 24) or
                    ((iterBytes[1].toInt() and 0xFF) shl 16) or
                    ((iterBytes[2].toInt() and 0xFF) shl 8) or
                    (iterBytes[3].toInt() and 0xFF)
                salt = combined.copyOfRange(offset, offset + SALT_SIZE); offset += SALT_SIZE
                iv = combined.copyOfRange(offset, offset + IV_SIZE); offset += IV_SIZE
                cipherBytes = combined.copyOfRange(offset, combined.size)
            } else {
                // Legacy format: salt|iv|ciphertext
                salt = combined.copyOfRange(0, SALT_SIZE)
                iv = combined.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
                cipherBytes = combined.copyOfRange(SALT_SIZE + IV_SIZE, combined.size)
            }

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password, salt, iterations, 256)
            try {
                val tmp = factory.generateSecret(spec)
                val tmpBytes = tmp.encoded
                try {
                    val key = SecretKeySpec(tmpBytes, "AES")

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcm = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, key, gcm)
                    val plain = cipher.doFinal(cipherBytes)
                    try {
                        plain
                    } finally {
                        try { plain.fill(0) } catch (_: Exception) { }
                    }
                } finally {
                    if (tmpBytes != null) try { tmpBytes.fill(0) } catch (_: Exception) { }
                }
            } finally {
                // Clear sensitive password material
                try { spec.clearPassword() } catch (_: Exception) { }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Create an encrypted backup file in the app cache directory and return the File.
     * The file content is the Base64-encoded (salt|iv|ciphertext) blob so it can be
     * easily shared via FileProvider.
     */
    fun createEncryptedBackupFile(context: Context, filename: String, entries: List<InsulinEntry>, password: CharArray): File? {
        try {
            val json = buildJsonBackup(entries)
            val encrypted = encryptBackupWithPassword(password, json.toByteArray(Charsets.UTF_8))
            val outFile = File(context.cacheDir, filename)
            FileOutputStream(outFile).use { it.write(encrypted.toByteArray(Charsets.UTF_8)) }
            return outFile
        } catch (_: Exception) {
            return null
        } finally {
            // Clear caller-provided password array (caller should pass a transient copy)
            try { password.fill('\u0000') } catch (_: Exception) { }
        }
    }
}
