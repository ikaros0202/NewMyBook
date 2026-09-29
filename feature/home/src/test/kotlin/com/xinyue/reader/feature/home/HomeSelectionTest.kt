package com.xinyue.reader.feature.home

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import org.junit.Test

class HomeSelectionTest {
    @Test
    fun `selects most recently opened unfinished book and excludes it from recents`() {
        val selection = selectHomeBooks(
            listOf(
                book("older", openedAt = 10),
                book("hero", openedAt = 30),
                book("finished", openedAt = 40, finished = true),
                book("recent", openedAt = 20),
            ),
        )

        assertThat(selection.hero?.book?.id).isEqualTo("hero")
        assertThat(selection.hero?.action).isEqualTo(HomeBookAction.CONTINUE)
        assertThat(selection.recent.map(Book::id)).containsExactly("finished", "recent").inOrder()
    }

    @Test
    fun `falls back to newest imported unfinished book then latest completed book`() {
        val unread = selectHomeBooks(
            listOf(book("older", createdAt = 10), book("newest", createdAt = 20)),
        )
        assertThat(unread.hero?.book?.id).isEqualTo("newest")
        assertThat(unread.hero?.action).isEqualTo(HomeBookAction.START)

        val completed = selectHomeBooks(
            listOf(
                book("completed-old", createdAt = 10, openedAt = 30, finished = true),
                book("completed-new", createdAt = 20, openedAt = 40, finished = true),
            ),
        )
        assertThat(completed.hero?.book?.id).isEqualTo("completed-new")
        assertThat(completed.hero?.action).isEqualTo(HomeBookAction.REOPEN)
    }

    @Test
    fun `returns an empty selection for an empty library`() {
        assertThat(selectHomeBooks(emptyList()).hero).isNull()
        assertThat(selectHomeBooks(emptyList()).recent).isEmpty()
    }

    private fun book(
        id: String,
        createdAt: Long = 1,
        openedAt: Long? = null,
        finished: Boolean = false,
    ) = Book(
        id = id,
        title = id,
        author = null,
        originalFileName = "$id.txt",
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash-$id",
        contentLength = 100,
        createdAtEpochMillis = createdAt,
        lastOpenedAtEpochMillis = openedAt,
        finished = finished,
    )
}
