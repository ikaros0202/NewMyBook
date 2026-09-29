package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.SearchChunkHit
import com.xinyue.reader.core.database.dao.SearchIndexDao
import com.xinyue.reader.core.database.entity.SearchChunkEntity
import com.xinyue.reader.core.database.entity.SearchIndexStateEntity
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.time.EpochClock
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class SearchIndexCoordinatorTest {
    @Test
    fun `successful rebuild is bounded and activates only after complete indexing`() = runTest {
        val content = "甲乙丙丁".repeat(180_000)
        val dao = FakeSearchIndexDao().apply {
            state.value = searchState(active = "old", building = null, status = SearchIndexStatus.READY)
            chunks += SearchChunkEntity(
                bookId = "book-1",
                generationId = "old",
                chapterStartOffset = null,
                startOffset = 0,
                content = "旧索引",
            )
        }
        val textSource = FakeTextSource(content)
        val coordinator = SearchIndexCoordinator(
            books = FakeBookRepository(sampleBook(content.length.toLong())),
            textSource = textSource,
            dao = dao,
            clock = EpochClock { 99L },
        )

        coordinator.build("book-1")

        assertThat(dao.state.value?.status).isEqualTo(SearchIndexStatus.READY)
        assertThat(dao.state.value?.indexedUtf16Length).isEqualTo(content.length.toLong())
        assertThat(dao.state.value?.activeGenerationId).isNotEqualTo("old")
        assertThat(dao.chunks.map { it.generationId }.toSet())
            .containsExactly(dao.state.value?.activeGenerationId)
        assertThat(dao.batchSizes).isNotEmpty()
        assertThat(dao.batchSizes.max()).isAtMost(64)
        assertThat(dao.chunks.maxOf { it.content.length }).isAtMost(16 * 1024)
        assertThat(textSource.maxAfter).isAtMost(256 * 1024)
        assertThat(dao.oldGenerationStayedActiveDuringInsert).isTrue()
    }

    @Test
    fun `failed rebuild removes only the building generation and keeps old active data`() = runTest {
        val content = "正文".repeat(200_000)
        val dao = FakeSearchIndexDao().apply {
            state.value = searchState(active = "old", building = null, status = SearchIndexStatus.READY)
            chunks += SearchChunkEntity(
                bookId = "book-1",
                generationId = "old",
                chapterStartOffset = null,
                startOffset = 0,
                content = "仍可搜索的旧索引",
            )
        }
        val coordinator = SearchIndexCoordinator(
            books = FakeBookRepository(sampleBook(content.length.toLong())),
            textSource = FakeTextSource(content, failOnRequest = 2),
            dao = dao,
            clock = EpochClock { 99L },
        )

        assertFailsWith<IOException> { coordinator.build("book-1") }

        assertThat(dao.state.value?.status).isEqualTo(SearchIndexStatus.ERROR)
        assertThat(dao.state.value?.errorMessage).isEqualTo("建立搜索索引失败")
        assertThat(dao.state.value?.activeGenerationId).isEqualTo("old")
        assertThat(dao.chunks.map { it.generationId }).containsExactly("old")
    }

    private class FakeTextSource(
        private val content: String,
        private val failOnRequest: Int? = null,
    ) : TextSource {
        var requests = 0
        var maxAfter = 0

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            requests++
            if (requests == failOnRequest) throw IOException("synthetic read failure")
            maxAfter = maxOf(maxAfter, afterUtf16Units)
            val start = (anchorOffset - beforeUtf16Units).coerceAtLeast(0).toInt()
            val end = (anchorOffset + afterUtf16Units).coerceAtMost(content.length.toLong()).toInt()
            return TextWindow(start.toLong(), content.substring(start, end), content.length.toLong())
        }
    }

    private class FakeSearchIndexDao : SearchIndexDao {
        val state = MutableStateFlow<SearchIndexStateEntity?>(null)
        val chunks = mutableListOf<SearchChunkEntity>()
        val batchSizes = mutableListOf<Int>()
        var oldGenerationStayedActiveDuringInsert = true

        override suspend fun getState(bookId: String): SearchIndexStateEntity? = state.value
        override fun observeState(bookId: String): Flow<SearchIndexStateEntity?> = state
        override suspend fun upsertState(state: SearchIndexStateEntity) { this.state.value = state }
        override suspend fun insertChunks(chunks: List<SearchChunkEntity>) {
            batchSizes += chunks.size
            oldGenerationStayedActiveDuringInsert =
                oldGenerationStayedActiveDuringInsert && state.value?.activeGenerationId == "old"
            this.chunks += chunks
        }
        override suspend fun deleteGeneration(bookId: String, generationId: String) {
            chunks.removeAll { it.bookId == bookId && it.generationId == generationId }
        }
        override suspend fun deleteOtherGenerations(bookId: String, activeGenerationId: String) {
            chunks.removeAll { it.bookId == bookId && it.generationId != activeGenerationId }
        }
        override suspend fun searchTrigram(
            bookId: String,
            matchQuery: String,
            limit: Int,
        ): List<SearchChunkHit> = emptyList()
        override suspend fun searchShort(
            bookId: String,
            likePattern: String,
            limit: Int,
        ): List<SearchChunkHit> = emptyList()
    }

    private class FakeBookRepository(private val book: Book) : BookRepository {
        override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = book.takeIf { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgress(bookId: String): ReadingProgress? = null
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private fun sampleBook(contentLength: Long) = Book(
        id = "book-1",
        title = "测试书",
        author = null,
        originalFileName = "book.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash",
        contentLength = contentLength,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun searchState(active: String?, building: String?, status: String) =
        SearchIndexStateEntity(
            bookId = "book-1",
            activeGenerationId = active,
            buildingGenerationId = building,
            status = status,
            contentSha256 = "hash",
            indexedUtf16Length = 0,
            updatedAtEpochMillis = 1,
            errorMessage = null,
        )
}
