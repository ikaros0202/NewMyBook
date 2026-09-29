package com.xinyue.reader.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeneratedBookCoverTest {
    @Test
    fun `formats four Chinese characters as a balanced two by two mark`() {
        assertThat(bookCoverLabel("雾港来信")).isEqualTo("雾港\n来信")
    }

    @Test
    fun `uses initials instead of wrapping a long latin filename`() {
        assertThat(bookCoverLabel("performance-large")).isEqualTo("PL")
        assertThat(bookCoverLabel("A Long Journey")).isEqualTo("ALJ")
    }
}
