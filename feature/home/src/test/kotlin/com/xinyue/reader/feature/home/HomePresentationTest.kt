package com.xinyue.reader.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomePresentationTest {
    @Test
    fun `reading duration stays compact and useful`() {
        assertThat(formatHomeReadingDuration(0)).isEqualTo("0 分钟")
        assertThat(formatHomeReadingDuration(59_000)).isEqualTo("<1 分钟")
        assertThat(formatHomeReadingDuration(65 * 60_000L)).isEqualTo("1 小时 5 分钟")
    }
}
