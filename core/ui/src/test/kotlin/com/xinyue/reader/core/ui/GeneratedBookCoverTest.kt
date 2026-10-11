package com.xinyue.reader.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeneratedBookCoverTest {
    @Test
    fun `keeps the complete Chinese title`() {
        assertThat(bookCoverLabel("雾港来信")).isEqualTo("雾港来信")
    }

    @Test
    fun `keeps the complete latin title`() {
        assertThat(bookCoverLabel("performance-large")).isEqualTo("performance-large")
        assertThat(bookCoverLabel("A Long Journey")).isEqualTo("A Long Journey")
    }
}
