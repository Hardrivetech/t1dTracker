package com.hardrivetech.t1dtracker

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        com.hardrivetech.t1dtracker.data.AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_1_to_2_preservesData() {
        // Create version 1 schema and insert a row (no 'notes' column)
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
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
            """.trimIndent())
            execSQL("INSERT INTO insulin_entries (timestamp, carbs, icr, currentGlucose, targetGlucose, isf, carbDose, correctionDose, totalDose) VALUES (1234567890, 50.0, 10.0, 140.0, 100.0, 50.0, 5.0, 0.8, 5.8)")
            close()
        }

        // Define the migration as in AppDatabase
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE insulin_entries ADD COLUMN notes TEXT")
            }
        }

        // Run migration
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Verify data is preserved and the new column exists (should be NULL)
        val cursor = migrated.query("SELECT timestamp, carbs, notes FROM insulin_entries")
        assertTrue(cursor.moveToFirst())
        assertEquals(1234567890L, cursor.getLong(0))
        assertEquals(50.0, cursor.getDouble(1), 0.001)
        assertTrue(cursor.isNull(2))
        cursor.close()
    }
}
