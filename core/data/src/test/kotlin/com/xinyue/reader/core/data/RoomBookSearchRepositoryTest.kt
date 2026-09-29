package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.SearchChunkHit
import org.junit.Test

class RoomBookSearchRepositoryTest {
    @Test
    fun `like metacharacters are escaped before the short query`() {
        assertThat("百分%下划_反斜\\".escapeSearchLike())
            .isEqualTo("百分\\%下划\\_反斜\\\\")
    }

    @Test
    fun `exact offsets and highlights are derived from chunk content`() {
        val hit = SearchChunkHit(
            rowId = 1,
            chapterStartOffset = 100,
            startOffset = 1_000,
            content = "开头星河璀璨中间星河璀璨结尾",
            snippet = "ignored",
        )

        val results = hit.exactSearchResults("星河璀璨").toList()

        assertThat(results.map { it.offset }).containsExactly(1_002L, 1_008L).inOrder()
        results.forEach { result ->
            assertThat(result.snippet.substring(result.highlightStart, result.highlightEnd))
                .isEqualTo("星河璀璨")
            assertThat(result.chapterStartOffset).isEqualTo(100)
        }
    }
}
