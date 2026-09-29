package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.xinyue.reader.core.database.entity.ChapterEntity

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startOffset ASC")
    suspend fun getForBook(bookId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Transaction
    suspend fun replaceForBook(bookId: String, chapters: List<ChapterEntity>) {
        require(chapters.all { it.bookId == bookId }) { "章节索引与书籍不匹配" }
        deleteForBook(bookId)
        if (chapters.isNotEmpty()) insertAll(chapters)
    }
}
