package com.hardrivetech.t1dtracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import com.hardrivetech.t1dtracker.data.AppDatabase

@RunWith(AndroidJUnit4::class)
class AppDatabasePlainDetectionTest {
    @Test
    fun usesPlainSqliteWhenExisting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath("t1d_db")

        // Ensure clean state
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        // Create a plain SQLite DB with the old schema (version 1)
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS insulin_entries (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              timestamp INTEGER NOT NULL,
              carbs REAL NOT NULL,
              icr REAL NOT NULL,
              currentGlucose REAL NOT NULL,
              targetGlucose REAL NOT NULL,
              isf REAL NOT NULL,
              carbDose REAL NOT NULL,
              correctionDose REAL NOT NULL,
              totalDose REAL NOT NULL
            )
            """.trimIndent()
        )
        // Insert a row we can identify
        sqlite.execSQL("INSERT INTO insulin_entries (timestamp, carbs, icr, currentGlucose, targetGlucose, isf, carbDose, correctionDose, totalDose) VALUES (2222222222, 12.5, 10.0, 110.0, 100.0, 50.0, 1.25, 0.2, 1.45)")
        sqlite.close()

        // Now get the AppDatabase instance; it should detect plain sqlite and open Room normally
        val appDb = AppDatabase.getInstance(context)
        val entries = appDb.insulinDao().getAll()

        assertTrue("Should find the inserted test row", entries.any { it.timestamp == 2222222222L && it.carbs == 12.5 })
    }
}
