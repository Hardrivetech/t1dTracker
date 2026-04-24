package com.hardrivetech.t1dtracker.data

import android.content.Context
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Migration backup helpers (list + restore) moved out of `AppDatabase`'s
 * companion to avoid cross-file companion resolution issues during compilation.
 */
fun listMigrationBackups(context: Context): List<Long> {
    try {
        val backupDir = File(context.filesDir, "db_migration_backups")
        if (!backupDir.exists()) return emptyList()
        val regex = Regex("\\.(\\d+)\\.bak$")
        val set = mutableSetOf<Long>()
        backupDir.listFiles()?.forEach { f ->
            val m = regex.find(f.name)
            if (m != null) {
                val ts = m.groupValues[1].toLongOrNull()
                if (ts != null) set.add(ts)
            }
        }
        return set.toList().sortedDescending()
    } catch (_: IOException) {
        return emptyList()
    }
}

suspend fun restoreMigrationBackup(context: Context, timestamp: Long): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("t1d_db")

            // Attempt to close current instance (best-effort)
            try {
                val inst = AppDatabase.getInstance(context)
                try { inst.close() } catch (e: android.database.sqlite.SQLiteException) {
                    AppLog.w(
                        "DBMigrationApi",
                        "Close DB failed: ${e.message}"
                    )
                } catch (e: IllegalStateException) { AppLog.w("DBMigrationApi", "Close DB failed: ${e.message}") }
            } catch (e: IllegalStateException) {
                AppLog.w("DBMigrationApi", "Get instance failed: ${e.message}")
            } catch (e: android.database.sqlite.SQLiteException) {
                AppLog.w("DBMigrationApi", "Get instance failed: ${e.message}")
            }

            // Attempt to clear singleton INSTANCE via reflection (best-effort)
            try {
                val appDbClass = Class.forName("com.hardrivetech.t1dtracker.data.AppDatabase")
                val companionField = appDbClass.getDeclaredField("Companion")
                companionField.isAccessible = true
                val companionObj = companionField.get(null)
                val instField = companionObj.javaClass.getDeclaredField("INSTANCE")
                instField.isAccessible = true
                instField.set(companionObj, null)
            } catch (e: ReflectiveOperationException) {
                AppLog.w("DBMigrationApi", "Reflection to clear INSTANCE failed: ${e.message}")
            } catch (e: SecurityException) {
                AppLog.w("DBMigrationApi", "Reflection security failure: ${e.message}")
            }

            val backupDir = File(context.filesDir, "db_migration_backups")
            val expectedNames = listOf(dbFile.name, dbFile.name + "-shm", dbFile.name + "-wal")
            for (name in expectedNames) {
                val bak = File(backupDir, "$name.$timestamp.bak")
                if (bak.exists()) {
                    val dst = if (name == dbFile.name) {
                        dbFile
                    } else {
                        File(
                            dbFile.absolutePath + name.substringAfter(dbFile.name)
                        )
                    }
                    dst.parentFile?.mkdirs()
                    bak.copyTo(dst, overwrite = true)
                }
            }

            AppLog.i("DBMigrationApi", "Restored DB backup $timestamp")
            true
        } catch (e: IOException) {
            AppLog.e("DBMigrationApi", "restoreMigrationBackup I/O failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "restoreMigrationBackup failed")
            false
        } catch (e: ReflectiveOperationException) {
            AppLog.e("DBMigrationApi", "restoreMigrationBackup reflection failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "restoreMigrationBackup failed")
            false
        } catch (e: SecurityException) {
            AppLog.e("DBMigrationApi", "restoreMigrationBackup security failed: ${e.message}", e)
            TelemetryUtil.recordException(e, "restoreMigrationBackup failed")
            false
        }
    }
