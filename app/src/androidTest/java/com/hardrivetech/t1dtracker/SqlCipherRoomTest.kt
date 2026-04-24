package com.hardrivetech.t1dtracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqlCipherRoomTest {
    @Test
    fun roomSqlCipher_roundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Load SQLCipher native libs
        SQLiteDatabase.loadLibs(context)

        val passphrase = "test-sqlcipher-pass".toByteArray(Charsets.UTF_8)
        val dbName = "t1d_db_sqlcipher"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val factory = SupportFactory(passphrase)
        val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        val dao = db.insulinDao()
        val entry = InsulinEntry(
            timestamp = System.currentTimeMillis(),
            carbs = 42.0,
            icr = 10.0,
            currentGlucose = 120.0,
            targetGlucose = 100.0,
            isf = 50.0,
            carbDose = 4.2,
            correctionDose = 0.4,
            totalDose = 4.6,
            notes = "sqlcipher-test"
        )

        val id = dao.insert(entry)
        val all = dao.getAll()
        assertTrue("Inserted item should be present in SQLCipher-backed Room DB", all.any { it.id == id })
    }
}
