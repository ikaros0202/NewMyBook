package com.xinyue.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xinyue.reader.core.data.BackupBookRecord
import com.xinyue.reader.core.data.BackupBookSource
import com.xinyue.reader.core.data.BackupCatalogSnapshot
import com.xinyue.reader.core.data.BackupEquivalenceVerifier
import com.xinyue.reader.core.data.RestoreJournal
import com.xinyue.reader.core.data.RestorePhase
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two-step fixture driven by qa/scripts/run-v1.2-backup-equivalence.ps1 around a real am force-stop. */
@RunWith(AndroidJUnit4::class)
class BackupRecoveryForceStopInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val dependencies = EntryPointAccessors.fromApplication(context, BackupDebugEntryPoint::class.java)

    @Test
    fun prepareFilesPublishedRecoveryBoundary() = runBlocking {
        assumeTrue(phase() == "prepare")
        cleanOperationalResidue()
        val expected = expectedCatalog()
        dependencies.restoreDatabaseGateway().replaceAll(expected)
        writeAssets("old")
        val snapshot = dependencies.restoreSnapshotStore().create(OPERATION_ID, expected, TARGETS)

        dependencies.restoreDatabaseGateway().replaceAll(
            expected.copy(books = expected.books.map { it.copy(title = "恢复中公开新标题") }),
        )
        writeAssets("new")
        File(context.filesDir, STAGED_ROOT).mkdirs()
        dependencies.restoreJournalStore().write(
            RestoreJournal(
                operationId = OPERATION_ID,
                phase = RestorePhase.FILES_PUBLISHED,
                stagedRoot = STAGED_ROOT,
                snapshotRoot = snapshot.root.relativeTo(context.filesDir).invariantSeparatorsPath,
                intendedTargets = TARGETS,
                publishedMoves = emptyList(),
                expectedCatalogSha256 = "0".repeat(64),
            ),
        )

        assertNotNull(dependencies.restoreJournalStore().read(OPERATION_ID))
        assertEquals("new-public-original", File(context.filesDir, TARGETS.first()).readText())
    }

    @Test
    fun verifyStartupRecoveryAfterForceStop() = runBlocking {
        assumeTrue(phase() == "verify")
        val deadline = System.currentTimeMillis() + 20_000
        while (dependencies.restoreJournalStore().read(OPERATION_ID) != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        val verifier = BackupEquivalenceVerifier(context.filesDir)
        val expected = verifier.capture(expectedCatalog())
        val actualCatalog = dependencies.restoreDatabaseGateway().snapshot()
        assertEquals(expectedCatalog().books, actualCatalog.books)
        val actual = verifier.compare(expected, actualCatalog)
        assertTrue(actual.mismatches.joinToString(), actual.mismatches.isEmpty())
        assertTrue(actual.equivalent)
        assertEquals("old-public-original", File(context.filesDir, TARGETS[0]).readText())
        assertEquals("old-public-normalized", File(context.filesDir, TARGETS[1]).readText())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(context.filesDir, TARGETS[2]).readBytes())
        assertTrue(File(context.filesDir, "restore-journals").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "restore-snapshots").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "backup-staging").listFiles().orEmpty().isEmpty())
    }

    private fun expectedCatalog(): BackupCatalogSnapshot {
        val normalized = "old-public-normalized".encodeToByteArray()
        val book = BackupBookRecord(
            id = BOOK_ID,
            title = "恢复前公开标题",
            author = null,
            originalFileName = "public-recovery.txt",
            charsetName = "UTF-8",
            contentSha256 = sha(normalized),
            contentLength = normalized.size.toLong(),
            createdAtEpochMillis = 1,
            originalAssetPath = "assets/books/$BOOK_ID/original.txt",
            normalizedAssetPath = "assets/books/$BOOK_ID/content.txt",
            offsetIndexAssetPath = "assets/books/$BOOK_ID/offsets.xidx",
        )
        return BackupCatalogSnapshot(
            books = listOf(book),
            bookSources = listOf(
                BackupBookSource(
                    BOOK_ID,
                    "books/$BOOK_ID/original.txt",
                    "books/$BOOK_ID/content.txt",
                    "books/$BOOK_ID/offsets.xidx",
                    null,
                ),
            ),
        )
    }

    private fun writeAssets(version: String) {
        File(context.filesDir, TARGETS[0]).also { it.parentFile!!.mkdirs(); it.writeText("$version-public-original") }
        File(context.filesDir, TARGETS[1]).writeText("$version-public-normalized")
        File(context.filesDir, TARGETS[2]).writeBytes(if (version == "old") byteArrayOf(1, 2, 3) else byteArrayOf(9, 8, 7))
    }

    private fun cleanOperationalResidue() {
        File(context.filesDir, "restore-journals").deleteRecursively()
        File(context.filesDir, "restore-snapshots").deleteRecursively()
        File(context.filesDir, "backup-staging").deleteRecursively()
    }

    private fun phase(): String? = InstrumentationRegistry.getArguments().getString("recoveryPhase")

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val OPERATION_ID = "device-force-stop"
        const val BOOK_ID = "device-recovery-book"
        const val STAGED_ROOT = "backup-staging/import-device-force-stop"
        val TARGETS = listOf(
            "books/$BOOK_ID/original.txt",
            "books/$BOOK_ID/content.txt",
            "books/$BOOK_ID/offsets.xidx",
        )
    }
}
