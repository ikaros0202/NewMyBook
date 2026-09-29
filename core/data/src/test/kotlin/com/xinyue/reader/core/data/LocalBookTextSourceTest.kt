package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalBookTextSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads only the requested absolute utf16 window and lazily builds a missing index`() = runTest {
        val root = requireNotNull(temporaryFolder.newFolder("library"))
        val content = "开头abc😀中段def结尾"
        val textFile = root.resolve("books/book-1/content.txt").apply {
            parentFile.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
        val source = LocalTextSource(root, StandardTestDispatcher(testScheduler))

        val window = source.readWindow(
            normalizedPath = "books/book-1/content.txt",
            anchorOffset = 8,
            beforeUtf16Units = 5,
            afterUtf16Units = 6,
        )

        assertThat(window.startOffset).isEqualTo(3)
        assertThat(window.text).isEqualTo(content.substring(3, 14))
        assertThat(window.totalUtf16Length).isEqualTo(content.length)
        assertThat(textFile.resolveSibling("offsets.xidx").isFile).isTrue()
    }

    @Test
    fun `never returns a window boundary inside a surrogate pair`() = runTest {
        val root = requireNotNull(temporaryFolder.newFolder("surrogate-library"))
        val content = "AB😀CD"
        root.resolve("books/book-1/content.txt").apply {
            parentFile.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
        val source = LocalTextSource(root, StandardTestDispatcher(testScheduler))

        val window = source.readWindow(
            normalizedPath = "books/book-1/content.txt",
            anchorOffset = 3,
            beforeUtf16Units = 0,
            afterUtf16Units = 1,
        )

        assertThat(window.text.first().isLowSurrogate()).isFalse()
        assertThat(window.text.last().isHighSurrogate()).isFalse()
    }

    @Test
    fun `rebuilds a corrupt or stale derived offset index without losing the book`() = runTest {
        val root = requireNotNull(temporaryFolder.newFolder("repair-library"))
        val content = "第一章 起航\n" + "海风".repeat(8_000)
        val textFile = root.resolve("books/book-1/content.txt").apply {
            parentFile.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
        val indexFile = textFile.resolveSibling("offsets.xidx").apply {
            writeText("broken derived cache")
        }
        val source = LocalTextSource(root, StandardTestDispatcher(testScheduler))

        val window = source.readWindow(
            normalizedPath = "books/book-1/content.txt",
            anchorOffset = 9_000,
            beforeUtf16Units = 120,
            afterUtf16Units = 240,
        )

        assertThat(window.text).isEqualTo(content.substring(8_880, 9_240))
        assertThat(Utf8OffsetIndexFile.read(indexFile).last().utf8ByteOffset)
            .isEqualTo(textFile.length())
    }
}
