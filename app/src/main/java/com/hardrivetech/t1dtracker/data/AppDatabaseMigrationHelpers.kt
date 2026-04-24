package com.hardrivetech.t1dtracker.data

import android.database.sqlite.SQLiteException
import com.hardrivetech.t1dtracker.AppLog
import com.hardrivetech.t1dtracker.TelemetryUtil
import java.io.IOException

internal suspend fun readEntriesSafe(currentDb: AppDatabase): List<InsulinEntry>? {
    return try {
        currentDb.insulinDao().getAll()
    } catch (e: SQLiteException) {
        AppLog.e("AppDatabase", "Failed to read existing DB: ${e.message}", e)
        TelemetryUtil.recordException(e, "migrate: read existing DB failed")
        null
    }
}

internal suspend fun importEntriesToDb(tempDb: AppDatabase, entries: List<InsulinEntry>): Boolean {
    return try {
        tempDb.withTransaction {
            val dao = tempDb.insulinDao()
            for (e in entries) {
                dao.insert(e.copy(id = 0L))
            }
        }
        true
    } catch (e: SQLiteException) {
        AppLog.e("AppDatabase", "Failed to import into encrypted DB: ${e.message}", e)
        TelemetryUtil.recordException(e, "migrate: import failed")
        try {
            tempDb.close()
        } catch (ioe: IOException) {
            AppLog.w("AppDatabase", "Failed closing tempDb after import failure: ${ioe.message}")
        }
        false
    }
}
