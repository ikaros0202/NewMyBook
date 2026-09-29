package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupOptions
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test

class BackupAssetInventoryTest {
    private lateinit var root: java.io.File

    @Before fun setUp() { root = createTempDirectory("backup-assets").toFile() }
    @After fun tearDown() { root.deleteRecursively() }

    @Test
    fun `complete inventory maps book cover and referenced or unreferenced fonts to opaque paths`() {
        write("books/book-1/original.txt", "original")
        write("books/book-1/content.txt", "content")
        write("books/book-1/offsets.xidx", "index")
        write("books/book-1/cover.webp", "cover")
        write("fonts/font-1/font.bin", "font one")
        write("fonts/font-2/font.bin", "font two")
        val result = BackupAssetInventory(root).collect(snapshot(), BackupOptions())

        assertThat(result.assets.map { it.archivePath }).containsExactly(
            "assets/books/book-1/original.txt",
            "assets/books/book-1/content.txt",
            "assets/books/book-1/offsets.xidx",
            "assets/books/book-1/cover.webp",
            "assets/fonts/font-1/font.bin",
            "assets/fonts/font-2/font.bin",
        ).inOrder()
        assertThat(result.assets.map { it.kind }).containsExactly(
            BackupEntryKind.ORIGINAL_TEXT,
            BackupEntryKind.NORMALIZED_TEXT,
            BackupEntryKind.OFFSET_INDEX,
            BackupEntryKind.CUSTOM_COVER,
            BackupEntryKind.FONT,
            BackupEntryKind.FONT,
        ).inOrder()
    }

    @Test
    fun `omitting text and fonts removes binary references but retains cover`() {
        write("books/book-1/original.txt", "original")
        write("books/book-1/content.txt", "content")
        write("books/book-1/offsets.xidx", "index")
        write("books/book-1/cover.webp", "cover")
        write("fonts/font-1/font.bin", "font one")
        write("fonts/font-2/font.bin", "font two")

        val result = BackupAssetInventory(root).collect(
            snapshot(),
            BackupOptions(includeBookText = false, includeFonts = false),
        )

        assertThat(result.assets.map { it.archivePath }).containsExactly("assets/books/book-1/cover.webp")
        assertThat(result.catalog.books.single().originalAssetPath).isNull()
        assertThat(result.catalog.books.single().normalizedAssetPath).isNull()
        assertThat(result.catalog.books.single().offsetIndexAssetPath).isNull()
        assertThat(result.catalog.fonts).isEmpty()
    }

    @Test
    fun `missing required book asset path escape symlink and duplicate physical file are rejected`() {
        write("books/book-1/original.txt", "original")
        write("books/book-1/content.txt", "content")
        write("books/book-1/cover.webp", "cover")
        write("fonts/font-1/font.bin", "font one")
        write("fonts/font-2/font.bin", "font two")
        assertFailsWith<IllegalArgumentException> { BackupAssetInventory(root).collect(snapshot(), BackupOptions()) }

        write("books/book-1/offsets.xidx", "index")
        val escaped = snapshot().copy(
            bookSources = listOf(snapshot().bookSources.single().copy(originalRelativePath = "../outside.txt")),
        )
        assertFailsWith<IllegalArgumentException> { BackupAssetInventory(root).collect(escaped, BackupOptions()) }

        val cover = root.resolve("books/book-1/cover.webp")
        assertFailsWith<IllegalArgumentException> {
            BackupAssetInventory(root, isSymbolicLink = { it == cover }).collect(snapshot(), BackupOptions())
        }

        val duplicate = snapshot().copy(
            fontSources = listOf(
                BackupFontSource("font-1", "fonts/font-1/font.bin"),
                BackupFontSource("font-2", "fonts/font-1/font.bin"),
            ),
        )
        assertFailsWith<IllegalArgumentException> { BackupAssetInventory(root).collect(duplicate, BackupOptions()) }
    }

    @Test
    fun `hashing aborts when a source changes size`() {
        val original = write("books/book-1/original.txt", "original")
        write("books/book-1/content.txt", "content")
        write("books/book-1/offsets.xidx", "index")
        write("books/book-1/cover.webp", "cover")
        write("fonts/font-1/font.bin", "font one")
        write("fonts/font-2/font.bin", "font two")
        var changed = false
        val inventory = BackupAssetInventory(
            root,
            openInput = { file ->
                object : FilterInputStream(file.inputStream()) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val count = super.read(buffer, offset, length)
                        if (!changed && file == original && count > 0) {
                            changed = true
                            file.appendText("changed")
                        }
                        return count
                    }
                }
            },
        )
        val asset = inventory.collect(snapshot(), BackupOptions()).assets.first()

        val error = assertFailsWith<IllegalStateException> { inventory.hash(asset) }
        assertThat(error).hasMessageThat().contains("发生变化")
    }

    private fun snapshot() = BackupCatalogSnapshot(
        books = listOf(
            BackupBookRecord(
                id = "book-1",
                title = "公开测试书",
                originalFileName = "public.txt",
                charsetName = "UTF-8",
                contentSha256 = "a".repeat(64),
                contentLength = 8,
                createdAtEpochMillis = 1,
                originalAssetPath = "assets/books/book-1/original.txt",
                normalizedAssetPath = "assets/books/book-1/content.txt",
                offsetIndexAssetPath = "assets/books/book-1/offsets.xidx",
                customCoverAssetPath = "assets/books/book-1/cover.webp",
            ),
        ),
        fonts = listOf(
            BackupFontRecord("font-1", "引用字体", "b".repeat(64), 8, 1, "assets/fonts/font-1/font.bin"),
            BackupFontRecord("font-2", "未引用字体", "c".repeat(64), 8, 2, "assets/fonts/font-2/font.bin"),
        ),
        bookSources = listOf(
            BackupBookSource(
                "book-1",
                "books/book-1/original.txt",
                "books/book-1/content.txt",
                "books/book-1/offsets.xidx",
                "books/book-1/cover.webp",
            ),
        ),
        fontSources = listOf(
            BackupFontSource("font-1", "fonts/font-1/font.bin"),
            BackupFontSource("font-2", "fonts/font-2/font.bin"),
        ),
    )

    private fun write(relativePath: String, text: String): java.io.File = root.resolve(relativePath).also {
        it.parentFile.mkdirs()
        it.writeText(text)
    }
}
