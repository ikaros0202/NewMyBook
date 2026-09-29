package com.xinyue.reader.core.ui

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import java.io.File
import org.junit.Test

class BookCoverModelTest {
    @Test
    fun `default and missing private models use generated cover fallback`() {
        assertThat(bookCoverModel(sampleBook())).isNull()
        assertThat(bookCoverModel(sampleBook().copy(customCoverPath = "books/book-1/cover.webp"))).isNull()
    }

    @Test
    fun `validated private model is used only with a stored cover reference`() {
        val privateFile = File("validated-private-cover.webp")
        val book = sampleBook().copy(
            customCoverPath = "books/book-1/cover.webp",
            customCoverModel = privateFile,
        )

        assertThat(bookCoverModel(book)).isEqualTo(privateFile)
        assertThat(bookCoverModel(book.copy(customCoverPath = null))).isNull()
    }

    private fun sampleBook() = Book(
        id = "book-1",
        title = "测试书",
        author = null,
        originalFileName = "fixture.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
