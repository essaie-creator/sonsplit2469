package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_files")
data class ProcessedFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalName: String,
    val vocalPath: String,
    val instrumentalPath: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long
)
