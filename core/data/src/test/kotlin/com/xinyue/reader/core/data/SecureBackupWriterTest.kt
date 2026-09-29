package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupOptions
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecureBackupWriterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `complete export has canonical catalogs verified entries and no private paths`() = runTest {
        val root = temporaryFolder.newFolder("private")
        val snapshot = completeSnapshot(root)
        val inventory = BackupAssetInventory(root)
        val collected = inventory.collect(snapshot, BackupOptions())
        val archive = temporaryFolder.newFile("complete.xinyuebackup")

        val written = SecureBackupWriter(inventory).write(
            archive = archive,
            catalog = collected.catalog,
            assets = collected.assets,
            options = BackupOptions(),
            appVersion = "1.2-test",
            createdAtEpochMillis = 123,
        )

        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertThat(entries.first().name).isEqualTo(BackupManifestCodec.MANIFEST_PATH)
            assertThat(entries.all { !it.isDirectory }).isTrue()
            assertThat(entries.map { it.name }).containsNoDuplicates()
            val manifest = BackupManifestCodec.decode(zip.getInputStream(entries.first()).readBytes())
            assertThat(manifest.entries.map { it.path }).isInOrder()
            assertThat(manifest.entries.map { it.path }).containsExactlyElementsIn(
                BackupManifestCodec.CATALOG_PATHS + listOf(
                    "assets/books/book-1/original.txt",
                    "assets/books/book-1/content.txt",
                    "assets/books/book-1/offsets.xidx",
                    "assets/books/book-1/cover.webp",
                    "assets/fonts/font-1/font.bin",
                ),
            )
            manifest.entries.forEach { expected ->
                val entry = zip.getEntry(expected.path)
                val bytes = zip.getInputStream(entry).readBytes()
                assertThat(bytes.size.toLong()).isEqualTo(expected.uncompressedSize)
                assertThat(sha256(bytes)).isEqualTo(expected.sha256)
            }
            assertThat(zip.getEntry("assets/books/book-1/cover.webp").method).isEqualTo(java.util.zip.ZipEntry.STORED)
            assertThat(zip.getEntry("assets/fonts/font-1/font.bin").method).isEqualTo(java.util.zip.ZipEntry.STORED)
            val allText = entries.joinToString("\n") { entry ->
                if (entry.name.endsWith(".json")) zip.getInputStream(entry).reader().readText() else ""
            }
            assertThat(allText).doesNotContain(root.absolutePath)
            assertThat(allText).doesNotContain("bookSources")
            assertThat(allText).doesNotContain("importTasks")
        }
        assertThat(written.bytesWritten).isEqualTo(archive.length())
        assertThat(written.archiveSha256).isEqualTo(sha256(archive.readBytes()))
    }

    @Test
    fun `catalogs and archive are deterministic and metadata-only export is honest`() = runTest {
        val root = temporaryFolder.newFolder("metadata-private")
        val inventory = BackupAssetInventory(root)
        val options = BackupOptions(includeBookText = false, includeFonts = false)
        val collected = inventory.collect(completeSnapshot(root), options)
        val first = temporaryFolder.newFile("first.xinyuebackup")
        val second = temporaryFolder.newFile("second.xinyuebackup")
        val writer = SecureBackupWriter(inventory)

        writer.write(first, collected.catalog, collected.assets, options, "1.2-test", 456)
        writer.write(second, collected.catalog, collected.assets, options, "1.2-test", 456)

        assertThat(first.readBytes()).isEqualTo(second.readBytes())
        ZipFile(first).use { zip ->
            val manifest = BackupManifestCodec.decode(zip.getInputStream(zip.getEntry("manifest.json")).readBytes())
            assertThat(manifest.options).isEqualTo(options)
            assertThat(manifest.entries.map { it.kind }).containsNoneOf(
                BackupEntryKind.ORIGINAL_TEXT,
                BackupEntryKind.NORMALIZED_TEXT,
                BackupEntryKind.OFFSET_INDEX,
                BackupEntryKind.FONT,
            )
            assertThat(zip.getInputStream(zip.getEntry("catalog/fonts.json")).reader().readText()).isEqualTo("[]")
            val books = zip.getInputStream(zip.getEntry("catalog/books.json")).reader().readText()
            assertThat(books).doesNotContain("originalAssetPath")
            assertThat(books).doesNotContain("normalizedAssetPath")
            assertThat(books).doesNotContain("offsetIndexAssetPath")
        }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation after a hashing chunk aborts writer`() = runTest {
        val root = temporaryFolder.newFolder("cancel-private")
        val inventory = BackupAssetInventory(root)
        val collected = inventory.collect(completeSnapshot(root), BackupOptions())
        SecureBackupWriter(inventory).write(
            temporaryFolder.newFile("cancel.xinyuebackup"), collected.catalog, collected.assets,
            BackupOptions(), "1.2-test", 1,
        ) { progress -> if (progress.label == "hashing-chunk") throw CancellationException("stop") }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation after a zip write chunk aborts writer`() = runTest {
        val root = temporaryFolder.newFolder("cancel-write-private")
        val inventory = BackupAssetInventory(root)
        val collected = inventory.collect(completeSnapshot(root), BackupOptions())
        SecureBackupWriter(inventory).write(
            temporaryFolder.newFile("cancel-write.xinyuebackup"), collected.catalog, collected.assets,
            BackupOptions(), "1.2-test", 1,
        ) { progress -> if (progress.label == "writing-chunk") throw CancellationException("stop") }
    }

    @Test(expected = BackupSourceChangedException::class)
    fun `same-size mutation between hashing and zip write is rejected`() = runTest {
        val root = temporaryFolder.newFolder("mutated-private")
        val opens = mutableMapOf<String, Int>()
        val inventory = BackupAssetInventory(root, openInput = { file ->
            val count = opens.getOrDefault(file.path, 0) + 1
            opens[file.path] = count
            if (count == 2 && file.name == "original.txt") file.writeText("changed content", Charsets.UTF_8)
            file.inputStream()
        })
        val collected = inventory.collect(completeSnapshot(root), BackupOptions())
        SecureBackupWriter(inventory).write(
            temporaryFolder.newFile("mutated.xinyuebackup"), collected.catalog, collected.assets,
            BackupOptions(), "1.2-test", 1,
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun completeSnapshot(root: File): BackupCatalogSnapshot {
        fun write(path: String, bytes: ByteArray): String = File(root, path).also {
            it.parentFile!!.mkdirs(); it.writeBytes(bytes)
        }.relativeTo(root).invariantSeparatorsPath
        val original = write("books/book-1/original.txt", "public original".encodeToByteArray())
        val content = write("books/book-1/content.txt", "public content".encodeToByteArray())
        val offsets = write("books/book-1/offsets.xidx", byteArrayOf(1, 2, 3))
        val cover = write("books/book-1/cover.webp", byteArrayOf(4, 5, 6))
        val font = write("fonts/font-1/font.bin", byteArrayOf(7, 8, 9))
        return BackupCatalogSnapshot(
            books = listOf(BackupBookRecord("book-1", "公共书名", originalFileName = "public.txt", charsetName = "UTF-8", contentSha256 = "a".repeat(64), contentLength = 14, createdAtEpochMillis = 1, originalAssetPath = "assets/books/book-1/original.txt", normalizedAssetPath = "assets/books/book-1/content.txt", offsetIndexAssetPath = "assets/books/book-1/offsets.xidx", customCoverAssetPath = "assets/books/book-1/cover.webp")),
            fonts = listOf(BackupFontRecord("font-1", "公共字体", "b".repeat(64), 3, 2, "assets/fonts/font-1/font.bin")),
            bookSources = listOf(BackupBookSource("book-1", original, content, offsets, cover)),
            fontSources = listOf(BackupFontSource("font-1", font)),
        )
    }
}
