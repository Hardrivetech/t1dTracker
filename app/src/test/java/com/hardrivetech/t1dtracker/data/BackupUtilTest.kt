package com.hardrivetech.t1dtracker.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class BackupUtilTest {
    @Test
    fun encryptAndDecryptBackup_roundTrip() {
        val entries = listOf(
            InsulinEntry(id = 0, timestamp = System.currentTimeMillis(), carbs = 50.0, icr = 10.0, currentGlucose = 150.0, targetGlucose = 100.0, isf = 50.0, carbDose = 5.0, correctionDose = 1.0, totalDose = 6.0, notes = "note"),
            InsulinEntry(id = 0, timestamp = System.currentTimeMillis() + 1, carbs = 30.0, icr = 12.0, currentGlucose = 120.0, targetGlucose = 100.0, isf = 50.0, carbDose = 2.5, correctionDose = 0.4, totalDose = 2.9)
        )

        val json = BackupUtil.buildJsonBackup(entries)
        assertTrue(json.contains("\"formatVersion\""))

        val password = "test-password".toCharArray()
        val encrypted = BackupUtil.encryptBackupWithPassword(password, json.toByteArray(StandardCharsets.UTF_8))
        assertNotNull(encrypted)

        val decrypted = BackupUtil.decryptBackupWithPassword("test-password".toCharArray(), encrypted)
        assertNotNull(decrypted)

        val decryptedJson = String(decrypted!!, StandardCharsets.UTF_8)
        // Count occurrences of the timestamp field to verify both entries present
        val count = "\"timestamp\"".toRegex().findAll(decryptedJson).count()
        assertEquals(2, count)
    }
}
