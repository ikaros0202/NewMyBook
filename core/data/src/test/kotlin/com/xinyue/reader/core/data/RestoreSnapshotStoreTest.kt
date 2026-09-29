package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreSnapshotStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `snapshot reload retains private book and font source mappings required by Room rollback`() {
        val root = temporary.newFolder("private")
        val bookSource = BackupBookSource(
            "book-a", "books/book-a/original.txt", "books/book-a/content.txt", "books/book-a/offsets.xidx", null,
        )
        val fontSource = BackupFontSource("font-a", "fonts/font-a/font.bin")
        val catalog = BackupCatalogSnapshot(
            books = listOf(
                BackupBookRecord(
                    "book-a", "公开书名", null, "public.txt", "UTF-8", "a".repeat(64), 1, 1,
                    originalAssetPath = "assets/books/book-a/original.txt",
                    normalizedAssetPath = "assets/books/book-a/content.txt",
                    offsetIndexAssetPath = "assets/books/book-a/offsets.xidx",
                ),
            ),
            fonts = listOf(BackupFontRecord("font-a", "公开字体", "b".repeat(64), 1, 1, "assets/fonts/font-a/font.bin")),
            bookSources = listOf(bookSource),
            fontSources = listOf(fontSource),
        )
        val targets = listOf(
            bookSource.originalRelativePath,
            bookSource.normalizedRelativePath,
            bookSource.offsetIndexRelativePath,
            fontSource.relativePath,
        )
        targets.forEach { relative ->
            File(root, relative).also { it.parentFile!!.mkdirs(); it.writeText("public") }
        }
        val store = RestoreSnapshotStore(root)

        val snapshot = store.create("operation", catalog, targets)
        val reloaded = store.load(snapshot.root)

        assertThat(reloaded.catalog.bookSources).containsExactly(bookSource)
        assertThat(reloaded.catalog.fontSources).containsExactly(fontSource)
    }
}
