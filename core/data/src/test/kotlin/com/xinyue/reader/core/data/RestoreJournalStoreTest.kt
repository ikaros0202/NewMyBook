package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreJournalStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `journal updates atomically through all durable phases using only relative paths`() {
        val root = temporaryFolder.newFolder("private")
        val store = RestoreJournalStore(root)
        var journal = journal(RestorePhase.PREPARED)

        store.write(journal)
        RestorePhase.values().drop(1).forEach { phase ->
            journal = journal.copy(phase = phase)
            store.write(journal)
            assertThat(store.read("operation-1")).isEqualTo(journal)
            assertThat(store.list().map { it.operationId }).containsExactly("operation-1")
        }

        assertThat(store.delete("operation-1")).isTrue()
        assertThat(store.list()).isEmpty()
        assertThat(root.walkTopDown().none { it.name.endsWith(".tmp") }).isTrue()
    }

    @Test
    fun `failed replacement preserves previous journal and removes sibling temp`() {
        val root = temporaryFolder.newFolder("failure-private")
        val stable = RestoreJournalStore(root)
        stable.write(journal(RestorePhase.PREPARED))
        val failing = RestoreJournalStore(root, beforeReplace = { throw IOException("injected") })

        val error = runCatching { failing.write(journal(RestorePhase.FILES_PUBLISHED)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(stable.read("operation-1")!!.phase).isEqualTo(RestorePhase.PREPARED)
        assertThat(root.walkTopDown().none { it.name.endsWith(".tmp") }).isTrue()
    }

    @Test
    fun `path escape absolute and malformed journal content are rejected`() {
        val root = temporaryFolder.newFolder("security-private")
        val store = RestoreJournalStore(root)
        val invalid = listOf(
            journal(RestorePhase.PREPARED).copy(stagedRoot = "../escape"),
            journal(RestorePhase.PREPARED).copy(snapshotRoot = "C:/escape"),
            journal(RestorePhase.PREPARED).copy(intendedTargets = listOf("/absolute")),
            journal(RestorePhase.PREPARED).copy(publishedMoves = listOf(PublishedMove("a\\b", false))),
        )

        invalid.forEach { value ->
            assertThat(runCatching { store.write(value) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    private fun journal(phase: RestorePhase) = RestoreJournal(
        operationId = "operation-1",
        phase = phase,
        stagedRoot = "backup-staging/import-operation-1",
        snapshotRoot = "restore-snapshots/operation-1",
        intendedTargets = listOf("books/book-1/content.txt"),
        publishedMoves = emptyList(),
        expectedCatalogSha256 = "a".repeat(64),
    )
}
