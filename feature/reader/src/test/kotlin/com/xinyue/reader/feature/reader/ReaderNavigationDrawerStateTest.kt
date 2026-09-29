package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Bookmark
import com.xinyue.reader.core.text.DetectedChapter
import org.junit.Test

class ReaderNavigationDrawerStateTest {
    private val chapters = listOf(
        DetectedChapter("序章", 0),
        DetectedChapter("第一章 重逢", 100),
        DetectedChapter("第二章 出发", 260),
    )

    @Test
    fun `directory search filters titles without changing chapter values`() {
        assertThat(filterReaderChapters(chapters, "重逢"))
            .containsExactly(DetectedChapter("第一章 重逢", 100))
        assertThat(filterReaderChapters(chapters, ""))
            .containsExactlyElementsIn(chapters)
            .inOrder()
    }

    @Test
    fun `current chapter uses the last chapter at or before anchor`() {
        assertThat(currentReaderChapter(chapters, 259)?.title).isEqualTo("第一章 重逢")
        assertThat(currentReaderChapter(chapters, 260)?.title).isEqualTo("第二章 出发")
    }

    @Test
    fun `bookmarks are presented in text order`() {
        val later = Bookmark("b2", "book", 200, null, 2)
        val earlier = Bookmark("b1", "book", 40, null, 1)

        assertThat(sortedReaderBookmarks(listOf(later, earlier)))
            .containsExactly(earlier, later)
            .inOrder()
    }
}
