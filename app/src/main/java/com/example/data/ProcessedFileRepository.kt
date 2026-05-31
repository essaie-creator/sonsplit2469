package com.example.data

import kotlinx.coroutines.flow.Flow

class ProcessedFileRepository(private val processedFileDao: ProcessedFileDao) {
    val allProcessedFiles: Flow<List<ProcessedFile>> = processedFileDao.getAllProcessedFiles()

    suspend fun insert(file: ProcessedFile): Long {
        return processedFileDao.insertProcessedFile(file)
    }

    suspend fun delete(file: ProcessedFile) {
        processedFileDao.deleteProcessedFile(file)
    }

    suspend fun deleteById(id: Int) {
        processedFileDao.deleteById(id)
    }

    suspend fun getById(id: Int): ProcessedFile? {
        return processedFileDao.getById(id)
    }
}
