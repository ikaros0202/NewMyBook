package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupProgress
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalBackupServiceExportTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `success copies only a self-validated private archive and cleans staging`() = runTest {
        val root = temporaryFolder.newFolder("private")
        val snapshot = oneBookSnapshot(root)
        val gateway = FakeDocumentGateway()
        val progress = mutableListOf<BackupProgress>()
        val service = service(root, snapshot, gateway)

        val result = service.export("content://public/destination", BackupOptions()) { progress += it }

        assertThat(result).isInstanceOf(BackupExportResult.Success::class.java)
        result as BackupExportResult.Success
        assertThat(result.operationId).isEqualTo("operation-1")
        assertThat(result.bytesWritten).isEqualTo(gateway.bytes.size.toLong())
        assertThat(gateway.bytes.copyOfRange(0, 2)).isEqualTo(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))
        assertThat(progress.zipWithNext().all { (before, after) -> after.completed >= before.completed }).isTrue()
        assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
        assertThat(gateway.invalidated).isFalse()
    }

    @Test
    fun `permission failure reports typed error without a destination success`() = runTest {
        val root = temporaryFolder.newFolder("permission-private")
        val gateway = FakeDocumentGateway(openFailure = SecurityException("private URI must not escape"))

        val result = service(root, oneBookSnapshot(root), gateway).export("content://redacted", BackupOptions())

        assertThat(result).isEqualTo(
            BackupExportResult.Failure(com.xinyue.reader.core.domain.model.BackupError(BackupErrorCode.PERMISSION, "无法写入所选备份位置")),
        )
        assertThat(gateway.bytes).isEmpty()
        assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `short write or no space invalidates destination and never reports success`() = runTest {
        for (failure in listOf(IOException("short write"), IOException("ENOSPC"))) {
            val root = temporaryFolder.newFolder("write-${failure.message}")
            val gateway = FakeDocumentGateway(writeFailure = failure)

            val result = service(root, oneBookSnapshot(root), gateway).export("content://redacted", BackupOptions())

            assertThat(result).isInstanceOf(BackupExportResult.Failure::class.java)
            assertThat((result as BackupExportResult.Failure).error.code).isIn(
                listOf(BackupErrorCode.PROVIDER, BackupErrorCode.SPACE),
            )
            assertThat(gateway.invalidated).isTrue()
            assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
        }
    }

    @Test
    fun `cancellation during archive work returns cancelled and removes private temp`() = runTest {
        val root = temporaryFolder.newFolder("cancel-private")
        val gateway = FakeDocumentGateway()

        val result = service(root, oneBookSnapshot(root), gateway).export("content://redacted", BackupOptions()) {
            if (it.label == "hashing-chunk") throw CancellationException("cancel")
        }

        assertThat(result).isEqualTo(BackupExportResult.Cancelled)
        assertThat(gateway.bytes).isEmpty()
        assertThat(File(root, "backup-staging").listFiles().orEmpty()).isEmpty()
    }

    private fun service(root: File, snapshot: BackupCatalogSnapshot, gateway: BackupDocumentGateway) =
        LocalBackupService(
            catalogDataSource = BackupCatalogDataSource { snapshot },
            assetInventory = BackupAssetInventory(root),
            archiveWriter = SecureBackupWriter(BackupAssetInventory(root)),
            documentGateway = gateway,
            privateRoot = root,
            appVersion = "1.2-test",
            nowEpochMillis = { 123 },
            idFactory = { "operation-1" },
        )

    private fun oneBookSnapshot(root: File): BackupCatalogSnapshot {
        fun source(path: String, text: String) = File(root, path).also { it.parentFile!!.mkdirs(); it.writeText(text) }
        val original = source("books/book-1/original.txt", "public original")
        val content = source("books/book-1/content.txt", "public content")
        val offsets = source("books/book-1/offsets.xidx", "public offsets")
        return BackupCatalogSnapshot(
            books = listOf(BackupBookRecord("book-1", "公共书名", originalFileName = "public.txt", charsetName = "UTF-8", contentSha256 = "a".repeat(64), contentLength = 14, createdAtEpochMillis = 1, originalAssetPath = "assets/books/book-1/original.txt", normalizedAssetPath = "assets/books/book-1/content.txt", offsetIndexAssetPath = "assets/books/book-1/offsets.xidx")),
            bookSources = listOf(BackupBookSource("book-1", original.relativeTo(root).invariantSeparatorsPath, content.relativeTo(root).invariantSeparatorsPath, offsets.relativeTo(root).invariantSeparatorsPath, null)),
        )
    }

    private class FakeDocumentGateway(
        private val openFailure: Throwable? = null,
        private val writeFailure: IOException? = null,
    ) : BackupDocumentGateway {
        val sink = ByteArrayOutputStream()
        val bytes get() = sink.toByteArray()
        var invalidated = false

        override fun openForWrite(destinationUri: String): OutputStream {
            openFailure?.let { throw it }
            return if (writeFailure == null) sink else object : OutputStream() {
                private var count = 0
                override fun write(value: Int) {
                    if (count++ >= 32) throw writeFailure
                    sink.write(value)
                }
            }
        }

        override fun invalidate(destinationUri: String): Boolean {
            invalidated = true
            sink.reset()
            return true
        }
    }
}
