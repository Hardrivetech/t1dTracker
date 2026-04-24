package com.hardrivetech.t1dtracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.hardrivetech.t1dtracker.data.AppDatabase
import com.hardrivetech.t1dtracker.data.InsulinEntry
import com.hardrivetech.t1dtracker.data.BackupUtil
import com.hardrivetech.t1dtracker.data.BackupImporter

@RunWith(AndroidJUnit4::class)
class ExportImportE2ETest {
    @Test
    fun backupImport_roundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getInstance(context)
        val dao = db.insulinDao()

        // Create a unique entry
        val timestamp = System.currentTimeMillis()
        val uniqueCarbs = (timestamp % 100000) / 1000.0
        val entry = InsulinEntry(
            timestamp = timestamp,
            carbs = uniqueCarbs,
            icr = 10.0,
            currentGlucose = 120.0,
            targetGlucose = 100.0,
            isf = 50.0,
            carbDose = uniqueCarbs / 10.0,
            correctionDose = (120.0 - 100.0) / 50.0,
            totalDose = uniqueCarbs / 10.0 + (120.0 - 100.0) / 50.0,
            notes = "e2e-test"
        )

        val id = dao.insert(entry)
        val allBefore = dao.getAll()
        assertTrue("Inserted entry should be present before backup", allBefore.any { it.id == id })

        // Create encrypted backup file
        val file = BackupUtil.createEncryptedBackupFile(context, "e2e_backup.t1d", allBefore, "testpass".toCharArray())
        assertNotNull("Backup file should be created", file)

        // Remove the inserted entry
        val inserted = dao.getAll().first { it.id == id }
        dao.delete(inserted)
        val afterDelete = dao.getAll()
        assertFalse("Entry should be deleted before import", afterDelete.any { it.id == id })

        // Import backup
        val ok = BackupImporter.importEncryptedBackupFile(context, db, file!!, "testpass".toCharArray())
        assertTrue("Import should succeed", ok)

        val afterImport = dao.getAll()
        assertTrue("Imported entries should contain the original item by timestamp+carbs",
            afterImport.any { it.timestamp == timestamp && it.carbs == uniqueCarbs })
    }
}
