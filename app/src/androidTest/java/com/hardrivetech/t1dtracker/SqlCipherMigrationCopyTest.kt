package com.hardrivetech.t1dtracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry

@RunWith(AndroidJUnit4::class)
class SqlCipherMigrationCopyTest {
    @Test
    fun migrate_plain_to_sqlcipher_by_copy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Create a plaintext DB and insert a row
        val plainName = "migration_plain.db"
        val plainFile = context.getDatabasePath(plainName)
        if (plainFile.exists()) plainFile.delete()
        plainFile.parentFile?.mkdirs()

        val plainDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(plainFile, null)
        plainDb.execSQL(
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
        plainDb.execSQL("INSERT INTO insulin_entries (timestamp, carbs, icr, currentGlucose, targetGlucose, isf, carbDose, correctionDose, totalDose) VALUES (3333333333, 25.0, 10.0, 130.0, 100.0, 50.0, 2.5, 0.6, 3.1)")

        val cursor = plainDb.rawQuery("SELECT timestamp, carbs, icr, currentGlucose, targetGlucose, isf, carbDose, correctionDose, totalDose FROM insulin_entries", null)
        val rows = mutableListOf<InsulinEntry>()
        while (cursor.moveToNext()) {
            rows.add(
                InsulinEntry(
                    timestamp = cursor.getLong(0),
                    carbs = cursor.getDouble(1),
                    icr = cursor.getDouble(2),
                    currentGlucose = cursor.getDouble(3),
                    targetGlucose = cursor.getDouble(4),
                    isf = cursor.getDouble(5),
                    carbDose = cursor.getDouble(6),
                    correctionDose = cursor.getDouble(7),
                    totalDose = cursor.getDouble(8),
                    notes = "migrated"
                )
            )
        }
        cursor.close()
        plainDb.close()

        // Create an encrypted Room DB and copy rows into it
        SQLiteDatabase.loadLibs(context)
        val pass = "migrate-pass".toByteArray(Charsets.UTF_8)
        val factory = SupportFactory(pass)
        val encName = "migration_encrypted.db"
        val encFile = context.getDatabasePath(encName)
        if (encFile.exists()) encFile.delete()

        val encryptedDb = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, encName)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        val dao = encryptedDb.insulinDao()
        for (r in rows) {
            dao.insert(r)
        }

        val all = dao.getAll()
        assertTrue(all.any { it.timestamp == 3333333333L && it.carbs == 25.0 })
    }
}
