package com.xinyue.reader.feature.library

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import org.junit.Test

class LibraryFilterTest {
    @Test
    fun `automatic views distinguish unread reading and effectively finished books`() {
        val unread = sampleBook("unread")
        val reading = sampleBook("reading")
        val finishedByFlag = sampleBook("finished-flag", finished = true)
        val finishedByProgress = sampleBook("finished-progress")

        assertThat(LibraryFilter.Unread.matches(unread, 0.0, NOW)).isTrue()
        assertThat(LibraryFilter.Unread.matches(reading, 0.2, NOW)).isFalse()
        assertThat(LibraryFilter.Reading.matches(reading, 0.2, NOW)).isTrue()
        assertThat(LibraryFilter.Reading.matches(finishedByFlag, 0.2, NOW)).isFalse()
        assertThat(LibraryFilter.Reading.matches(finishedByProgress, 1.0, NOW)).isFalse()
        assertThat(LibraryFilter.Finished.matches(finishedByFlag, 0.2, NOW)).isTrue()
        assertThat(LibraryFilter.Finished.matches(finishedByProgress, 1.0, NOW)).isTrue()
        assertThat(LibraryFilter.Finished.matches(unread, 0.0, NOW)).isFalse()
    }

    @Test
    fun `recent includes only books opened during the latest thirty days`() {
        val justInside = sampleBook("inside", lastOpenedAt = NOW - THIRTY_DAYS_MILLIS + 1)
        val boundary = sampleBook("boundary", lastOpenedAt = NOW - THIRTY_DAYS_MILLIS)
        val tooOld = sampleBook("old", lastOpenedAt = NOW - THIRTY_DAYS_MILLIS - 1)
        val unopened = sampleBook("unopened", lastOpenedAt = null)
        val future = sampleBook("future", lastOpenedAt = NOW + 1)

        assertThat(LibraryFilter.Recent.matches(justInside, 0.0, NOW)).isTrue()
        assertThat(LibraryFilter.Recent.matches(boundary, 0.0, NOW)).isTrue()
        assertThat(LibraryFilter.Recent.matches(tooOld, 0.0, NOW)).isFalse()
        assertThat(LibraryFilter.Recent.matches(unopened, 0.0, NOW)).isFalse()
        assertThat(LibraryFilter.Recent.matches(future, 0.0, NOW)).isFalse()
    }

    @Test
    fun `custom group and all filters use only explicit membership`() {
        val grouped = sampleBook("grouped", groupId = "group-1")
        assertThat(LibraryFilter.All.matches(grouped, 0.0, NOW)).isTrue()
        assertThat(LibraryFilter.Group("group-1").matches(grouped, 0.0, NOW)).isTrue()
        assertThat(LibraryFilter.Group("group-2").matches(grouped, 0.0, NOW)).isFalse()
    }

    private fun sampleBook(
        id: String,
        groupId: String? = null,
        finished: Boolean = false,
        lastOpenedAt: Long? = null,
    ) = Book(
        id = id,
        title = "测试书-$id",
        author = null,
        originalFileName = "$id.txt",
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash-$id",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = lastOpenedAt,
        groupId = groupId,
        finished = finished,
    )

    private companion object {
        const val NOW = 4_000_000_000L
    }
}
