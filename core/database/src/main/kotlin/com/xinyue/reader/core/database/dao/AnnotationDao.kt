package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.xinyue.reader.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY id")
    suspend fun getAllForBackup(): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY startOffset, createdAtEpochMillis")
    fun observeForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY startOffset, createdAtEpochMillis")
    suspend fun getForBook(bookId: String): List<AnnotationEntity>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND kind = :kind " +
            "ORDER BY startOffset, createdAtEpochMillis",
    )
    fun observeForBookAndKind(bookId: String, kind: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE id = :annotationId")
    suspend fun get(annotationId: String): AnnotationEntity?

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND " +
            "((startOffset < :endOffset AND endOffset > :startOffset) OR " +
            "(startOffset = endOffset AND startOffset >= :startOffset AND startOffset < :endOffset)) " +
            "ORDER BY startOffset, createdAtEpochMillis",
    )
    suspend fun findOverlapping(
        bookId: String,
        startOffset: Long,
        endOffset: Long,
    ): List<AnnotationEntity>

    @Upsert
    suspend fun upsert(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :annotationId")
    suspend fun delete(annotationId: String)

}
