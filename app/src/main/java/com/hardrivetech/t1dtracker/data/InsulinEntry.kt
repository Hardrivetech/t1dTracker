package com.hardrivetech.t1dtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "insulin_entries")
data class InsulinEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val carbs: Double,
    val icr: Double,
    val currentGlucose: Double,
    val targetGlucose: Double,
    val isf: Double,
    val carbDose: Double,
    val correctionDose: Double,
    val totalDose: Double,
    val notes: String? = null
)
