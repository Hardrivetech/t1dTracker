package com.hardrivetech.t1dtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface InsulinDao {
    @Insert
    suspend fun insert(entry: InsulinEntry): Long

    @Update
    suspend fun update(entry: InsulinEntry): Int

    @Delete
    suspend fun delete(entry: InsulinEntry): Int

    @Query("SELECT * FROM insulin_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<InsulinEntry>
}
