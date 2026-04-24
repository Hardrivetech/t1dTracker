package com.hardrivetech.t1dtracker

import android.content.Context
import android.net.Uri
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.BackupImporter
import com.hardrivetech.t1dtracker.data.BackupUtil
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsActions {
    @Suppress("TooGenericExceptionCaught")
    suspend fun performExportToUri(
        context: Context,
        db: AppDatabase,
        uri: Uri,
        password: CharArray
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entries = db.insulinDao().getAll()
                val json = BackupUtil.buildJsonBackup(entries)
                val encrypted = BackupUtil.encryptBackupWithPassword(
                    password,
                    json.toByteArray(Charsets.UTF_8)
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(encrypted.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: IOException) {
                AppLog.e("SettingsActions", "Export failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Export failed in SettingsActions.performExportToUri")
                false
            } catch (e: GeneralSecurityException) {
                AppLog.e("SettingsActions", "Export failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Export failed in SettingsActions.performExportToUri")
                false
            } catch (e: IllegalArgumentException) {
                AppLog.e("SettingsActions", "Export failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Export failed in SettingsActions.performExportToUri")
                false
            } finally {
                kotlin.runCatching {
                    password.fill('\u0000')
                }.onFailure { e ->
                    AppLog.w("SettingsActions", "Password zeroing failed: ${e.message}")
                    TelemetryUtil.recordException(e, "SettingsActions: password zeroing failed")
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun performImportFromUri(
        context: Context,
        db: AppDatabase,
        uri: Uri,
        password: CharArray
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val ins = context.contentResolver.openInputStream(uri) ?: return@withContext false
                val tmp = File(context.cacheDir, "import_tmp.t1d")
                ins.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                BackupImporter.importEncryptedBackupFile(context, db, tmp, password)
            } catch (e: IOException) {
                AppLog.e("SettingsActions", "Import failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Import failed in SettingsActions.performImportFromUri")
                false
            } catch (e: GeneralSecurityException) {
                AppLog.e("SettingsActions", "Import failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Import failed in SettingsActions.performImportFromUri")
                false
            } catch (e: IllegalArgumentException) {
                AppLog.e("SettingsActions", "Import failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Import failed in SettingsActions.performImportFromUri")
                false
            } finally {
                kotlin.runCatching {
                    password.fill('\u0000')
                }.onFailure { e ->
                    AppLog.w("SettingsActions", "Password zeroing failed: ${e.message}")
                    TelemetryUtil.recordException(e, "SettingsActions: password zeroing failed")
                }
            }
        }
    }
}
