package com.hardrivetech.t1dtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.BackupImporter
import com.hardrivetech.t1dtracker.data.BackupUtil
import com.hardrivetech.t1dtracker.data.listMigrationBackups
import com.hardrivetech.t1dtracker.data.restoreMigrationBackup
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

    @Suppress("TooGenericExceptionCaught")
    suspend fun createPreMigrationBackup(
        context: Context,
        db: AppDatabase,
        password: CharArray
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val entries = db.insulinDao().getAll()
                BackupUtil.createEncryptedBackupFile(
                    context,
                    "pre_migration_backup_${System.currentTimeMillis()}.t1d",
                    entries,
                    password
                )
            } catch (e: Exception) {
                AppLog.e("SettingsActions", "createPreMigrationBackup failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "createPreMigrationBackup failed")
                null
            } finally {
                kotlin.runCatching { password.fill('\u0000') }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun performShareBackup(db: AppDatabase, backupPassword: String, context: Context) {
        val entries = withContext(Dispatchers.IO) { db.insulinDao().getAll() }
        val file = withContext(Dispatchers.IO) {
            BackupUtil.createEncryptedBackupFile(
                context,
                "backup.t1d",
                entries,
                backupPassword.toCharArray()
            )
        }
        if (file != null) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, context.getString(R.string.share_backup)))
            } catch (e: android.content.ActivityNotFoundException) {
                AppLog.e("SettingsActions", "Share failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Share backup failed")
                Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                AppLog.e("SettingsActions", "Share failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Share backup failed")
                Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                AppLog.e("SettingsActions", "Share failed: ${e.message}", e)
                TelemetryUtil.recordException(e, "Share backup failed")
                Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.backup_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
}

// Wrappers for top-level migration helpers to provide a consistent API surface
@Suppress("TooGenericExceptionCaught")
suspend fun performMigration(context: Context, db: AppDatabase): Boolean {
    return com.hardrivetech.t1dtracker.performMigration(context, db)
}

@Suppress("TooGenericExceptionCaught")
suspend fun listPreMigrationBackups(context: Context): List<Long> {
    return com.hardrivetech.t1dtracker.listPreMigrationBackups(context)
}

@Suppress("TooGenericExceptionCaught")
suspend fun performRestoreMigration(context: Context, timestamp: Long): Boolean {
    return com.hardrivetech.t1dtracker.performRestoreMigration(context, timestamp)
}

@Suppress("TooGenericExceptionCaught")
suspend fun rotateKey(context: Context): Boolean {
    return com.hardrivetech.t1dtracker.rotateKey(context)
}

suspend fun performMigration(context: Context, db: AppDatabase): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            AppDatabase.migratePlaintextToEncrypted(context, db)
        } catch (e: Exception) {
            AppLog.e("SettingsActions", "performMigration failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "performMigration failed")
            false
        }
    }
}

suspend fun listPreMigrationBackups(context: Context): List<Long> {
    return withContext(Dispatchers.IO) {
        try {
            listMigrationBackups(context)
        } catch (e: Exception) {
            AppLog.e("SettingsActions", "listPreMigrationBackups failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "listPreMigrationBackups failed")
            emptyList()
        }
    }
}

suspend fun performRestoreMigration(context: Context, timestamp: Long): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            restoreMigrationBackup(context, timestamp)
        } catch (e: Exception) {
            AppLog.e("SettingsActions", "performRestoreMigration failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "performRestoreMigration failed")
            false
        }
    }
}

suspend fun rotateKey(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            EncryptionUtil.rotateKey(context)
        } catch (e: Exception) {
            AppLog.e("SettingsActions", "rotateKey failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "rotateKey failed")
            false
        }
    }
}
