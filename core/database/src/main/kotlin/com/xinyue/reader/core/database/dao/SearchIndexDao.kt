package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.xinyue.reader.core.database.entity.SearchChunkEntity
import com.xinyue.reader.core.database.entity.SearchIndexStateEntity
import kotlinx.coroutines.flow.Flow

data class SearchChunkHit(
    val rowId: Long,
    val chapterStartOffset: Long?,
    val startOffset: Long,
    val content: String,
    val snippet: String,
)

@Dao
interface SearchIndexDao {
    @Query("SELECT * FROM search_index_states WHERE bookId = :bookId")
    suspend fun getState(bookId: String): SearchIndexStateEntity?

    @Query("SELECT * FROM search_index_states WHERE bookId = :bookId")
    fun observeState(bookId: String): Flow<SearchIndexStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SearchIndexStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<SearchChunkEntity>)

    @Query("DELETE FROM search_chunks WHERE bookId = :bookId AND generationId = :generationId")
    suspend fun deleteGeneration(bookId: String, generationId: String)

    @Query("DELETE FROM search_chunks WHERE bookId = :bookId AND generationId != :activeGenerationId")
    suspend fun deleteOtherGenerations(bookId: String, activeGenerationId: String)

    @Query(
        """
        SELECT c.rowid AS rowId, c.chapterStartOffset, c.startOffset, c.content,
               snippet(search_chunks_fts, 0, '⟦', '⟧', '…', 24) AS snippet
        FROM search_chunks_fts
        JOIN search_chunks AS c ON c.rowid = search_chunks_fts.rowid
        JOIN search_index_states AS s ON s.bookId = c.bookId
        WHERE search_chunks_fts MATCH :matchQuery
          AND c.bookId = :bookId
          AND c.generationId = s.activeGenerationId
        ORDER BY rank
        LIMIT :limit
        """,
    )
    suspend fun searchTrigram(bookId: String, matchQuery: String, limit: Int): List<SearchChunkHit>

    @Query(
        """
        SELECT c.rowid AS rowId, c.chapterStartOffset, c.startOffset, c.content,
               c.content AS snippet
        FROM search_chunks AS c
        JOIN search_index_states AS s ON s.bookId = c.bookId
        WHERE c.bookId = :bookId
          AND c.generationId = s.activeGenerationId
          AND c.content LIKE :likePattern ESCAPE '\'
        ORDER BY c.startOffset
        LIMIT :limit
        """,
    )
    suspend fun searchShort(bookId: String, likePattern: String, limit: Int): List<SearchChunkHit>

    @Transaction
    suspend fun beginBuild(state: SearchIndexStateEntity) {
        val previousBuilding = getState(state.bookId)?.buildingGenerationId
        if (previousBuilding != null && previousBuilding != state.buildingGenerationId) {
            deleteGeneration(state.bookId, previousBuilding)
        }
        upsertState(state)
    }

    @Transaction
    suspend fun activate(
        state: SearchIndexStateEntity,
        generationId: String,
    ) {
        require(state.activeGenerationId == generationId)
        require(state.buildingGenerationId == null)
        upsertState(state)
        deleteOtherGenerations(state.bookId, generationId)
    }

    @Transaction
    suspend fun failBuild(
        state: SearchIndexStateEntity,
        failedGenerationId: String,
    ) {
        deleteGeneration(state.bookId, failedGenerationId)
        upsertState(state)
    }
}
