package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CharacterPaginatorTest {
    @Test
    fun `paginates without splitting a surrogate pair`() {
        val text = "甲乙😀丙丁"

        val first = CharacterPaginator.pageAt(text = text, anchorOffset = 0, maxUtf16Units = 3)
        val second = CharacterPaginator.pageAt(text = text, anchorOffset = first.endOffset, maxUtf16Units = 3)

        assertThat(first.text).isEqualTo("甲乙")
        assertThat(second.text).startsWith("😀")
        assertThat(first.endOffset).isEqualTo(second.startOffset)
    }

    @Test
    fun `one utf16 unit budget still advances across a complete code point`() {
        val page = CharacterPaginator.pageAt(text = "😀", anchorOffset = 0, maxUtf16Units = 1)

        assertThat(page.startOffset).isEqualTo(0)
        assertThat(page.endOffset).isEqualTo(2)
        assertThat(page.text).isEqualTo("😀")
    }

    @Test
    fun `clamps an anchor to the valid text range`() {
        val page = CharacterPaginator.pageAt(text = "短文", anchorOffset = 99, maxUtf16Units = 20)

        assertThat(page.startOffset).isEqualTo(2)
        assertThat(page.endOffset).isEqualTo(2)
        assertThat(page.text).isEmpty()
    }
}
