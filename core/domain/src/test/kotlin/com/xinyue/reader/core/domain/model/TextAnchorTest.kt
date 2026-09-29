package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextAnchorTest {
    @Test
    fun `clamp preserves the repair fingerprint`() {
        val anchor = TextAnchor(
            offset = 120,
            contextHash = "legacy",
            prefix = "前文",
            suffix = "后文",
        )

        assertThat(anchor.clampTo(100)).isEqualTo(
            TextAnchor(
                offset = 100,
                contextHash = "legacy",
                prefix = "前文",
                suffix = "后文",
            ),
        )
    }

    @Test
    fun `legacy anchors default to an empty repair context`() {
        assertThat(TextAnchor(offset = 8, contextHash = "hash"))
            .isEqualTo(TextAnchor(8, "hash", prefix = "", suffix = ""))
    }

    @Test
    fun `clamps an anchor into the current text length`() {
        assertThat(TextAnchor(offset = -4, contextHash = "a").clampTo(20).offset).isEqualTo(0)
        assertThat(TextAnchor(offset = 30, contextHash = "b").clampTo(20).offset).isEqualTo(20)
    }

    @Test
    fun `reading progress uses character offsets rather than page numbers`() {
        val progress = ReadingProgress(
            bookId = "book-1",
            anchor = TextAnchor(offset = 25, contextHash = "hash"),
            contentLength = 100,
            updatedAtEpochMillis = 10,
        )

        assertThat(progress.fraction).isWithin(0.0001).of(0.25)
    }

    @Test
    fun `empty content has zero progress`() {
        val progress = ReadingProgress(
            bookId = "book-1",
            anchor = TextAnchor(offset = 25, contextHash = "hash"),
            contentLength = 0,
            updatedAtEpochMillis = 10,
        )

        assertThat(progress.fraction).isEqualTo(0.0)
    }
}
