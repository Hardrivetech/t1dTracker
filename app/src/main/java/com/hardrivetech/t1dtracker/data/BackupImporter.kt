package com.hardrivetech.t1dtracker.data

import android.content.Context
import androidx.room.withTransaction
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object BackupImporter {
    /**
     * Decrypts and imports a backup file created by `BackupUtil`.
     * Returns true on success.
     */
    suspend fun importEncryptedBackupFile(context: Context, db: AppDatabase, file: File, password: CharArray): Boolean {
        try {
            val b64 = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
            val plain = BackupUtil.decryptBackupWithPassword(password, b64) ?: return false
            val root = JSONObject(String(plain, Charsets.UTF_8))
            val arr = root.optJSONArray("entries") ?: return false

            val toInsert = mutableListOf<InsulinEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val entry = InsulinEntry(
                    id = 0L,
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    carbs = o.optDouble("carbs", 0.0),
                    icr = o.optDouble("icr", 0.0),
                    currentGlucose = o.optDouble("currentGlucose", 0.0),
                    targetGlucose = o.optDouble("targetGlucose", 0.0),
                    isf = o.optDouble("isf", 0.0),
                    carbDose = o.optDouble("carbDose", 0.0),
                    correctionDose = o.optDouble("correctionDose", 0.0),
                    totalDose = o.optDouble("totalDose", 0.0),
                    notes = if (o.has("notes")) o.optString("notes", null) else null
                )
                toInsert.add(entry)
            }

            // Insert using suspend-friendly transaction
            try {
                db.withTransaction {
                    val dao = db.insulinDao()
                    for (e in toInsert) {
                        dao.insert(e)
                    }
                }
            } catch (e: Exception) {
                AppLog.e("BackupImporter", "DB insert failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile DB insert failed")
                return false
            }

            return true
        } catch (t: Throwable) {
            AppLog.e("BackupImporter", "importEncryptedBackupFile failed: ${t.message}", t)
            TelemetryUtil.recordException(t, "BackupImporter.importEncryptedBackupFile failed")
            return false
        } finally {
            try { password.fill('\u0000') } catch (_: Exception) { }
        }
    }
}
