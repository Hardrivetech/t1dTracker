package com.hardrivetech.t1dtracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupImporterInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        try { db.close() } catch (e: Exception) {
            AppLog.w("BackupImporterInstrumentedTest", "Close DB failed: ${e.message}")
        }
    }

    @Test
    fun importEncryptedBackupFile_insertsEntries() = runBlocking {
        val entries = listOf(
            InsulinEntry(
                id = 0,
                timestamp = System.currentTimeMillis(),
                carbs = 45.0,
                icr = 10.0,
                currentGlucose = 140.0,
                targetGlucose = 100.0,
                isf = 50.0,
                carbDose = 4.5,
                correctionDose = 0.8,
                totalDose = 5.3
            ),
            InsulinEntry(
                id = 0,
                timestamp = System.currentTimeMillis() + 1,
                carbs = 20.0,
                icr = 12.0,
                currentGlucose = 120.0,
                targetGlucose = 100.0,
                isf = 50.0,
                carbDose = 1.7,
                correctionDose = 0.4,
                totalDose = 2.1
            )
        )

        val json = BackupUtil.buildJsonBackup(entries)
        val password = "test-password".toCharArray()
        val encrypted = BackupUtil.encryptBackupWithPassword(
            password.copyOf(),
            json.toByteArray(StandardCharsets.UTF_8)
        )

        val outFile = File(context.cacheDir, "test_backup.b64")
        outFile.writeText(encrypted, Charsets.UTF_8)

        val ok = BackupImporter.importEncryptedBackupFile(context, db, outFile, "test-password".toCharArray())
        assertTrue(ok)

        val all = db.insulinDao().getAll()
        assertEquals(entries.size, all.size)

        try { outFile.delete() } catch (e: Exception) {
            AppLog.w("BackupImporterInstrumentedTest", "Delete temp file failed: ${e.message}")
        }
    }
}
