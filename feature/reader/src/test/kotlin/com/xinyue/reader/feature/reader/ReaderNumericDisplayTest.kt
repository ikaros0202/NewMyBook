package com.xinyue.reader.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNumericDisplayTest {
    @Test
    fun `integer controls retain significant trailing zeroes`() {
        assertEquals("20", formatNumber(20f, 0))
        assertEquals("100", formatNumber(100f, 0))
        assertEquals("0", formatNumber(0f, 0))
        assertEquals("1.6", formatNumber(1.6f, 4))
        assertEquals("0.0375", formatNumber(0.0375f, 4))
    }
}
