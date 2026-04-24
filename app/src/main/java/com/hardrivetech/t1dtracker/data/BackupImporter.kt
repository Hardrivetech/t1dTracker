package com.hardrivetech.t1dtracker.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
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
            } catch (e: SQLiteException) {
                AppLog.e("BackupImporter", "DB insert failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile DB insert failed")
                return false
            }

            return true
        } catch (e: IOException) {
            AppLog.e("BackupImporter", "importEncryptedBackupFile I/O failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile failed")
            return false
        } catch (e: JSONException) {
            AppLog.e("BackupImporter", "importEncryptedBackupFile JSON parse failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile failed")
            return false
        } catch (e: GeneralSecurityException) {
            AppLog.e("BackupImporter", "importEncryptedBackupFile crypto failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile failed")
            return false
        } catch (e: IllegalArgumentException) {
            AppLog.e("BackupImporter", "importEncryptedBackupFile bad data: ${e.message}", e)
            TelemetryUtil.recordException(e, "BackupImporter.importEncryptedBackupFile failed")
            return false
        } finally {
            password.fill('\u0000')
        }
    }
}
