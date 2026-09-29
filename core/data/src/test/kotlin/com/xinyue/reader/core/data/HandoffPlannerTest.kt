package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import kotlin.test.assertFailsWith
import org.junit.Test

class HandoffPlannerTest {
    private val planner = HandoffPlanner()

    @Test
    fun `without text requires exact content hash and rejects a renamed backup`() {
        val incoming = handoffCatalog(book("source", "a"))
        assertFailsWith<IllegalArgumentException> {
            planner.plan("token", incoming, handoffCatalog(book("target", "b")), manifest(false), emptySet(), 0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            planner.plan(
                "token",
                incoming,
                handoffCatalog(book("target", "a")),
                manifest(false).copy(archiveType = BackupArchiveType.BACKUP),
                emptySet(),
                0,
                0,
            )
        }
    }

    @Test
    fun `with text imports a missing book while an exact hash merges state`() {
        val incoming = handoffCatalog(book("source", "a", includeAssets = true))
        val imported = planner.plan(
            "token-a", incoming, BackupCatalogSnapshot(), manifest(true), assetPaths(), 10, 0,
        )
        val merged = planner.plan(
            "token-b", incoming, handoffCatalog(book("target", "a")), manifest(true), assetPaths(), 10, 0,
        )

        assertThat(imported.entityActions.single { it.kind == RestoreEntityKind.BOOK }.action)
            .isEqualTo(RestoreActionKind.INSERT)
        assertThat(merged.entityActions.single { it.kind == RestoreEntityKind.BOOK }.action)
            .isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(merged.bookIdRemap["source"]).isEqualTo("target")
    }

    @Test
    fun `same annotation conflict uses deterministic copy id and repeated import is idempotent`() {
        val local = annotation("note-1", "target", note = "本机内容")
        val incomingAnnotation = annotation("note-1", "source", note = "接力内容")
        val incoming = handoffCatalog(book("source", "a"), annotations = listOf(incomingAnnotation))
        val current = handoffCatalog(book("target", "a"), annotations = listOf(local))

        val first = planner.plan("token-a", incoming, current, manifest(false), emptySet(), 0, 0)
        val copy = first.entityActions.single { it.kind == RestoreEntityKind.ANNOTATION }
        assertThat(copy.action).isEqualTo(RestoreActionKind.COPY_AS_NEW)
        assertThat(copy.targetId).startsWith("handoff-")

        val currentAfterFirst = current.copy(
            annotations = current.annotations + incomingAnnotation.copy(id = copy.targetId, bookId = "target"),
        )
        val repeated = planner.plan("token-b", incoming, currentAfterFirst, manifest(false), emptySet(), 0, 0)
        val repeatAction = repeated.entityActions.single { it.kind == RestoreEntityKind.ANNOTATION }
        assertThat(repeatAction.action).isEqualTo(RestoreActionKind.SKIP_IDENTICAL)
        assertThat(repeated.conflicts).isEmpty()
    }

    private fun manifest(includeText: Boolean) = BackupManifest(
        archiveType = BackupArchiveType.HANDOFF,
        rootBookId = "source",
        appVersion = "test",
        createdAtEpochMillis = 1,
        options = BackupOptions(includeText, false),
        entries = BackupManifestCodec.V2_CATALOG_PATHS.map { path ->
            BackupManifestEntry(path, BackupEntryKind.CATALOG, 1, "c".repeat(64))
        } + if (includeText) listOf(
            BackupManifestEntry(
                "assets/books/source/original.txt",
                BackupEntryKind.ORIGINAL_TEXT,
                1,
                "d".repeat(64),
            ),
            BackupManifestEntry(
                "assets/books/source/content.txt",
                BackupEntryKind.NORMALIZED_TEXT,
                1,
                "e".repeat(64),
            ),
            BackupManifestEntry(
                "assets/books/source/offsets.xidx",
                BackupEntryKind.OFFSET_INDEX,
                1,
                "f".repeat(64),
            ),
        ) else emptyList(),
    )

    private fun handoffCatalog(
        book: BackupBookRecord,
        annotations: List<BackupAnnotationRecord> = emptyList(),
    ) = BackupCatalogSnapshot(books = listOf(book), annotations = annotations)

    private fun book(id: String, hashSeed: String, includeAssets: Boolean = false) = BackupBookRecord(
        id = id,
        title = "公开书名",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = hashSeed.repeat(64),
        contentLength = 100,
        createdAtEpochMillis = 1,
        originalAssetPath = if (includeAssets) "assets/books/source/original.txt" else null,
        normalizedAssetPath = if (includeAssets) "assets/books/source/content.txt" else null,
        offsetIndexAssetPath = if (includeAssets) "assets/books/source/offsets.xidx" else null,
    )

    private fun annotation(id: String, bookId: String, note: String) = BackupAnnotationRecord(
        id, bookId, "NOTE", 1, 2, "before", "after", "c".repeat(64), "yellow", note, 1, 2,
    )

    private fun assetPaths() = setOf(
        "assets/books/source/original.txt",
        "assets/books/source/content.txt",
        "assets/books/source/offsets.xidx",
    )
}
