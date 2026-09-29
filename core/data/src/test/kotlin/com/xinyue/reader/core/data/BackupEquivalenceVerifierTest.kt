package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import java.io.File
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupEquivalenceVerifierTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `sorted logical records and canonical asset hashes are equivalent`() {
        val root = temporary.newFolder("root")
        val catalog = catalog(root, listOf("book-b", "book-a"))
        val verifier = BackupEquivalenceVerifier(root)
        val expected = verifier.capture(catalog)
        val reordered = catalog.copy(
            books = catalog.books.reversed(),
            bookSources = catalog.bookSources.reversed(),
        )

        val result = verifier.compare(expected, reordered)

        assertThat(result.equivalent).isTrue()
        assertThat(result.mismatches).isEmpty()
        assertThat(result.canonicalDigest).matches("[0-9a-f]{64}")
    }

    @Test
    fun `changed missing or escaping assets fail without exposing private content`() {
        val root = temporary.newFolder("changed")
        val catalog = catalog(root, listOf("book-a"))
        val verifier = BackupEquivalenceVerifier(root)
        val expected = verifier.capture(catalog)
        File(root, "books/book-a/content.txt").writeText("changed-public-fixture")
        val changed = verifier.compare(expected, catalog)
        assertThat(changed.equivalent).isFalse()
        assertThat(changed.mismatches).contains("asset:book:book-a:normalized")
        assertThat(changed.toString()).doesNotContain("changed-public-fixture")

        File(root, "books/book-a/original.txt").delete()
        val missing = verifier.compare(expected, catalog)
        assertThat(missing.mismatches).contains("asset:book:book-a:original")

        val escaped = catalog.copy(
            bookSources = listOf(catalog.bookSources.single().copy(originalRelativePath = "../outside.txt")),
        )
        assertThat(runCatching { verifier.capture(escaped) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `settings JSON representation differences remain logically equivalent`() {
        val root = temporary.newFolder("semantic-settings")
        val compact = catalog(root, listOf("book-a")).copy(
            globalSettings = BackupGlobalSettingsRecord("{}", "{}", 1),
            bookSettings = listOf(BackupBookSettingsRecord("book-a", "{}", 1)),
            themes = listOf(BackupThemeRecord("theme-a", "公开主题", "{}", false, 1)),
        )
        val expanded = compact.copy(
            globalSettings = compact.globalSettings!!.copy(
                settingsJson = readerDataJson.encodeToString(ReaderSettings()),
                scheduleJson = readerDataJson.encodeToString(StoredReaderThemeSchedule()),
            ),
            bookSettings = listOf(
                compact.bookSettings.single().copy(
                    overridesJson = readerDataJson.encodeToString(ReaderSettingsOverrides()),
                ),
            ),
            themes = listOf(
                compact.themes.single().copy(settingsJson = readerDataJson.encodeToString(ReaderSettings())),
            ),
        )
        val verifier = BackupEquivalenceVerifier(root)

        assertThat(verifier.compare(verifier.capture(compact), expanded).equivalent).isTrue()
    }

    private fun catalog(root: File, ids: List<String>): BackupCatalogSnapshot {
        val books = ids.mapIndexed { index, id ->
            val dir = File(root, "books/$id").apply { mkdirs() }
            File(dir, "original.txt").writeText("public-original-$index")
            File(dir, "content.txt").writeText("public-normalized-$index")
            File(dir, "offsets.xidx").writeBytes(byteArrayOf(index.toByte()))
            BackupBookRecord(
                id, "公开书名-$index", null, "public.txt", "UTF-8", "a".repeat(64),
                File(dir, "content.txt").length(), 1,
                originalAssetPath = "assets/books/$id/original.txt",
                normalizedAssetPath = "assets/books/$id/content.txt",
                offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
            )
        }
        return BackupCatalogSnapshot(
            books = books,
            bookSources = ids.map {
                BackupBookSource(it, "books/$it/original.txt", "books/$it/content.txt", "books/$it/offsets.xidx", null)
            },
        )
    }
}
