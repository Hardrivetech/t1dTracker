package com.hardrivetech.t1dtracker.data

import android.content.Context
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.File
import java.io.IOException

internal fun createDbMigrationBackups(context: Context, origFiles: List<File>) {
    val backupDir = File(context.filesDir, "db_migration_backups")
    if (!backupDir.exists()) backupDir.mkdirs()
    val ts = System.currentTimeMillis()
    origFiles.filter { it.exists() }.forEach { f ->
        try {
            val dst = File(backupDir, "${f.name}.$ts.bak")
            f.copyTo(dst, overwrite = true)
        } catch (e: IOException) {
            AppLog.w("AppDatabase", "Failed to copy ${f.name} to backup: ${e.message}")
        }
    }
}

internal fun replaceDbFiles(origFiles: List<File>, tempFiles: List<File>): Boolean {
    return try {
        origFiles.forEach { if (it.exists()) it.delete() }
        tempFiles.zip(origFiles)
            .filter { (src, _) -> src.exists() }
            .forEach { (src, dst) ->
                if (!src.renameTo(dst)) {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                }
            }
        true
    } catch (e: IOException) {
        AppLog.e("AppDatabase", "Failed to atomically replace DB files: ${e.message}", e)
        TelemetryUtil.recordException(e, "migrate: file replace failed")
        false
    }
}
