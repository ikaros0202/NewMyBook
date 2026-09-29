package com.xinyue.reader.core.database

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.mapper.toDomain
import com.xinyue.reader.core.database.mapper.toEntity
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.TextAnchor
import org.junit.Test

class BookEntityMapperTest {
    @Test
    fun `book entity preserves content identity and private file paths`() {
        val entity = BookEntity(
            id = "book-1",
            title = "测试小说",
            author = "作者",
            originalFileName = "测试小说.txt",
            originalPath = "books/book-1/original.txt",
            normalizedPath = "books/book-1/content.txt",
            charsetName = "GB18030",
            contentSha256 = "abc123",
            contentLength = 2048,
            createdAtEpochMillis = 10,
            lastOpenedAtEpochMillis = 20,
        )

        val book = entity.toDomain()

        assertThat(book.id).isEqualTo("book-1")
        assertThat(book.contentSha256).isEqualTo("abc123")
        assertThat(book.normalizedPath).isEqualTo("books/book-1/content.txt")
        assertThat(book.contentLength).isEqualTo(2048)
    }

    @Test
    fun `progress entity restores a stable text anchor`() {
        val entity = ReadingProgressEntity(
            bookId = "book-1",
            offset = 42,
            contextHash = "context",
            prefix = "前文",
            suffix = "后文",
            contentLength = 100,
            updatedAtEpochMillis = 99,
        )

        val progress = entity.toDomain()

        assertThat(progress.anchor.offset).isEqualTo(42)
        assertThat(progress.anchor.contextHash).isEqualTo("context")
        assertThat(progress.anchor.prefix).isEqualTo("前文")
        assertThat(progress.anchor.suffix).isEqualTo("后文")
        assertThat(progress.fraction).isWithin(0.0001).of(0.42)
    }

    @Test
    fun `progress domain model persists its repair fingerprint`() {
        val progress = ReadingProgress(
            bookId = "book-1",
            anchor = TextAnchor(
                offset = 42,
                contextHash = "context",
                prefix = "前文",
                suffix = "后文",
            ),
            contentLength = 100,
            updatedAtEpochMillis = 99,
        )

        val entity = progress.toEntity()

        assertThat(entity.prefix).isEqualTo("前文")
        assertThat(entity.suffix).isEqualTo("后文")
    }
}
