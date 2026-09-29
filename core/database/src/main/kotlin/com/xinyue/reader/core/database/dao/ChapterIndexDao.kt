package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.xinyue.reader.core.database.entity.ChapterConfigEntity
import com.xinyue.reader.core.database.entity.ChapterEntity

@Dao
interface ChapterIndexDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startOffset ASC")
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapter_configs WHERE bookId = :bookId")
    suspend fun getConfig(bookId: String): ChapterConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ChapterConfigEntity)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: String)

    @Transaction
    suspend fun replaceAutomatically(
        bookId: String,
        chapters: List<ChapterEntity>,
        ruleSet: String,
        updatedAtEpochMillis: Long,
    ): Boolean {
        require(chapters.all { it.bookId == bookId }) { "Chapter index does not match book" }
        if (getConfig(bookId)?.manuallyEdited == true) return false
        replaceChapters(bookId, chapters)
        upsertConfig(ChapterConfigEntity(bookId, ruleSet, false, updatedAtEpochMillis))
        return true
    }

    @Transaction
    suspend fun replaceManually(
        bookId: String,
        chapters: List<ChapterEntity>,
        fallbackRuleSet: String,
        updatedAtEpochMillis: Long,
    ) {
        require(chapters.all { it.bookId == bookId }) { "Chapter index does not match book" }
        val ruleSet = getConfig(bookId)?.ruleSet ?: fallbackRuleSet
        replaceChapters(bookId, chapters)
        upsertConfig(ChapterConfigEntity(bookId, ruleSet, true, updatedAtEpochMillis))
    }

    @Transaction
    suspend fun replaceForRuleChange(
        bookId: String,
        chapters: List<ChapterEntity>,
        ruleSet: String,
        updatedAtEpochMillis: Long,
    ) {
        require(chapters.all { it.bookId == bookId }) { "Chapter index does not match book" }
        replaceChapters(bookId, chapters)
        upsertConfig(ChapterConfigEntity(bookId, ruleSet, false, updatedAtEpochMillis))
    }

    suspend fun replaceChapters(bookId: String, chapters: List<ChapterEntity>) {
        deleteChapters(bookId)
        if (chapters.isNotEmpty()) insertChapters(chapters)
    }
}
