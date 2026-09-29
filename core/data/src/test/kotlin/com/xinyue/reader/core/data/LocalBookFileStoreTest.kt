package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

class LocalBookFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `streams a legacy txt into original and normalized private files`() = runTest {
        val root = temporaryFolder.newFolder("library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val originalBytes = "第一章\r\n正文😀".toByteArray(Charset.forName("GB18030"))
        val source = ImportSource(
            displayName = "小说.txt",
            sizeBytes = originalBytes.size.toLong(),
            openStream = { ByteArrayInputStream(originalBytes) },
        )

        val staged = store.stage(bookId = "book-1", source = source, preferredCharsetName = "GB18030")
        val stored = store.commit(staged)

        assertThat(staged.contentSha256).isEqualTo(originalBytes.sha256())
        assertThat(staged.charsetName).isEqualTo("GB18030")
        assertThat(staged.contentLength).isEqualTo("第一章\n正文😀".length)
        assertThat(staged.suggestedTitle).isEqualTo("小说")
        assertThat(staged.suggestedAuthor).isNull()
        assertThat(root.resolve(stored.originalPath).readBytes()).isEqualTo(originalBytes)
        assertThat(root.resolve(stored.normalizedPath).readText()).isEqualTo("第一章\n正文😀")
        val checkpoints = Utf8OffsetIndexFile.read(
            root.resolve(stored.normalizedPath).resolveSibling("offsets.xidx"),
        )
        assertThat(checkpoints.first()).isEqualTo(Utf8OffsetCheckpoint(0, 0))
        assertThat(checkpoints.last().utf16Offset).isEqualTo(staged.contentLength)
        assertThat(checkpoints.last().utf8ByteOffset)
            .isEqualTo(root.resolve(stored.normalizedPath).length())
    }

    @Test
    fun `stages explicit series metadata from the normalized preview`() = runTest {
        val root = temporaryFolder.newFolder("series-metadata-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val bytes = """
            书名：长夜列车
            作者：林川
            系列：星海纪事
            卷序：3

            第一章
            正文
        """.trimIndent().toByteArray()

        val staged = store.stage(
            bookId = "series-book",
            source = ImportSource("fallback.txt", bytes.size.toLong()) { ByteArrayInputStream(bytes) },
            preferredCharsetName = "UTF-8",
        )

        assertThat(staged.suggestedSeriesName).isEqualTo("星海纪事")
        assertThat(staged.suggestedSeriesOrder).isEqualTo(3)
    }

    @Test
    fun `imports UTF-8 BOM text when the 64 KiB probe ends inside a character`() = runTest {
        val root = temporaryFolder.newFolder("split-boundary-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val originalBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            ByteArray(65_531) { 'a'.code.toByte() } +
            "界\r\n第二章\r\n正文".toByteArray()

        val staged = store.stage(
            bookId = "split-boundary-book",
            source = ImportSource("边界.txt", originalBytes.size.toLong()) {
                ByteArrayInputStream(originalBytes)
            },
            preferredCharsetName = "UTF-8",
        )

        assertThat(staged.charsetName).isEqualTo("UTF-8")
        assertThat(root.resolve("import-staging/split-boundary-book/content.txt").readText())
            .endsWith("界\n第二章\n正文")
    }

    @Test
    fun `rejects malformed UTF-8 bytes after a valid charset probe`() = runTest {
        val root = temporaryFolder.newFolder("late-malformed-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val originalBytes = ByteArray(64 * 1024) { 'a'.code.toByte() } +
            byteArrayOf(0xC3.toByte(), 0x28) +
            "正文".toByteArray()

        val error = assertFailsWith<IllegalArgumentException> {
            store.stage(
                bookId = "late-malformed-book",
                source = ImportSource("后段乱码.txt", originalBytes.size.toLong()) {
                    ByteArrayInputStream(originalBytes)
                },
                preferredCharsetName = "UTF-8",
            )
        }

        assertThat(error).hasMessageThat().contains("无法按 UTF-8 解码")
        assertThat(root.resolve("import-staging/late-malformed-book").exists()).isFalse()
    }

    @Test
    fun `rejects a txt whose decoded body contains only whitespace`() = runTest {
        val root = temporaryFolder.newFolder("blank-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val bytes = " \r\n\t　\n".toByteArray()

        val error = assertFailsWith<IllegalArgumentException> {
            store.stage(
                bookId = "blank-book",
                source = ImportSource("空白.txt", bytes.size.toLong()) { ByteArrayInputStream(bytes) },
                preferredCharsetName = "UTF-8",
            )
        }

        assertThat(error).hasMessageThat().contains("有效正文")
        assertThat(root.resolve("import-staging/blank-book").exists()).isFalse()
    }

    @Test
    fun `rejects a binary file renamed to txt`() = runTest {
        val root = temporaryFolder.newFolder("binary-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val bytes = ByteArray(256) { index -> if (index % 3 == 0) 0 else index.toByte() }

        val error = assertFailsWith<IllegalArgumentException> {
            store.stage(
                bookId = "binary-book",
                source = ImportSource("伪装.txt", bytes.size.toLong()) { ByteArrayInputStream(bytes) },
                preferredCharsetName = "UTF-8",
            )
        }

        assertThat(error).hasMessageThat().contains("有效 TXT")
        assertThat(root.resolve("import-staging/binary-book").exists()).isFalse()
    }

    @Test
    fun `rejects an import before copying when private storage is insufficient`() = runTest {
        val root = temporaryFolder.newFolder("full-library")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            availableBytes = { 128L },
        )
        val bytes = "第一章 正文".toByteArray()

        val error = assertFailsWith<IllegalArgumentException> {
            store.stage(
                bookId = "no-space-book",
                source = ImportSource("空间不足.txt", bytes.size.toLong()) { ByteArrayInputStream(bytes) },
                preferredCharsetName = "UTF-8",
            )
        }

        assertThat(error).hasMessageThat().contains("存储空间不足")
        assertThat(root.resolve("import-staging/no-space-book").exists()).isFalse()
    }

    @Test
    fun `removal rejects paths from different book directories`() = runTest {
        val root = temporaryFolder.newFolder("safe-removal-library")
        val firstDirectory = root.resolve("books/book-1").apply { mkdirs() }
        val secondDirectory = root.resolve("books/book-2").apply { mkdirs() }
        firstDirectory.resolve("original.txt").writeText("one")
        secondDirectory.resolve("content.txt").writeText("two")
        val store = LocalBookFileStore(
            rootDirectory = root,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertFailsWith<IllegalArgumentException> {
            store.remove(
                StoredBookFiles(
                    originalPath = "books/book-1/original.txt",
                    normalizedPath = "books/book-2/content.txt",
                ),
            )
        }

        assertThat(firstDirectory.exists()).isTrue()
        assertThat(secondDirectory.exists()).isTrue()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
