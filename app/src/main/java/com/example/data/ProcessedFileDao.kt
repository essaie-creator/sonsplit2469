package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedFileDao {
    @Query("SELECT * FROM processed_files ORDER BY timestamp DESC")
    fun getAllProcessedFiles(): Flow<List<ProcessedFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessedFile(file: ProcessedFile): Long

    @Delete
    suspend fun deleteProcessedFile(file: ProcessedFile)

    @Query("DELETE FROM processed_files WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM processed_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ProcessedFile?
}
