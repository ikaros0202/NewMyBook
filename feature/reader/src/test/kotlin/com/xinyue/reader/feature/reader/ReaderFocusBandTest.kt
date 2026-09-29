package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderFocusBandTest {
    @Test
    fun `one through eight lines stay centered in the viewport`() {
        val lines = (0 until 10).map { index ->
            ReaderLineBounds(topPx = index * 20f, bottomPx = (index + 1) * 20f)
        }

        (1..8).forEach { visibleLines ->
            val rect = calculateReaderFocusBandRect(
                enabled = true,
                visibleLines = visibleLines,
                viewportHeightPx = 200f,
                lines = lines,
            )

            checkNotNull(rect)
            assertThat(rect.bottomPx - rect.topPx).isEqualTo(visibleLines * 20f)
            assertThat(kotlin.math.abs((rect.topPx + rect.bottomPx) / 2f - 100f))
                .isAtMost(10f)
        }
    }

    @Test
    fun `requested lines clamp to a short page`() {
        val rect = calculateReaderFocusBandRect(
            enabled = true,
            visibleLines = 8,
            viewportHeightPx = 400f,
            lines = listOf(
                ReaderLineBounds(10f, 40f),
                ReaderLineBounds(40f, 70f),
            ),
        )

        assertThat(rect).isEqualTo(ReaderFocusBandRect(topPx = 10f, bottomPx = 70f))
    }

    @Test
    fun `geometry remains line based at two hundred percent font size`() {
        val lines = (0 until 10).map { index ->
            ReaderLineBounds(topPx = index * 80f, bottomPx = (index + 1) * 80f)
        }

        val rect = calculateReaderFocusBandRect(
            enabled = true,
            visibleLines = 3,
            viewportHeightPx = 800f,
            lines = lines,
        )

        checkNotNull(rect)
        assertThat(rect.bottomPx - rect.topPx).isEqualTo(240f)
        assertThat(kotlin.math.abs((rect.topPx + rect.bottomPx) / 2f - 400f)).isAtMost(40f)
    }

    @Test
    fun `disabled invalid viewport and empty layout draw no band`() {
        val line = listOf(ReaderLineBounds(0f, 40f))

        assertThat(calculateReaderFocusBandRect(false, 3, 400f, line)).isNull()
        assertThat(calculateReaderFocusBandRect(true, 3, 0f, line)).isNull()
        assertThat(calculateReaderFocusBandRect(true, 3, 400f, emptyList())).isNull()
    }

    @Test
    fun `invalid line bounds are ignored without producing an inverted rectangle`() {
        val rect = calculateReaderFocusBandRect(
            enabled = true,
            visibleLines = 3,
            viewportHeightPx = 200f,
            lines = listOf(
                ReaderLineBounds(0f, 30f),
                ReaderLineBounds(30f, 30f),
                ReaderLineBounds(70f, 50f),
            ),
        )

        assertThat(rect).isEqualTo(ReaderFocusBandRect(0f, 30f))
    }
}
