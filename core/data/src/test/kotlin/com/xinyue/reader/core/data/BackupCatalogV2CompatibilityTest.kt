package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupCatalogV2CompatibilityTest {
    @Test
    fun `v2 round trip preserves series and multiple collection memberships`() {
        val snapshot = BackupCatalogSnapshot(
            books = listOf(book(seriesName = "公开系列", seriesOrder = 3)),
            groups = listOf(group("group-a"), group("group-b")),
            memberships = listOf(
                BackupBookMembershipRecord("book-1", "group-a"),
                BackupBookMembershipRecord("book-1", "group-b"),
            ),
        )

        val decoded = decodeWritten(BackupCatalogCodec.encode(snapshot), formatVersion = 2)

        assertThat(decoded.books.single().seriesName).isEqualTo("公开系列")
        assertThat(decoded.books.single().seriesOrder).isEqualTo(3)
        assertThat(decoded.memberships).containsExactlyElementsIn(snapshot.memberships)
    }

    @Test
    fun `v1 catalog converts legacy groupId into one membership`() {
        val legacyBook = book(seriesName = null, seriesOrder = null).copy(groupId = "legacy-group")
        val legacy = BackupCatalogCodec.encode(
            BackupCatalogSnapshot(
                books = listOf(legacyBook),
                groups = listOf(group("legacy-group")),
            ),
        ).filterKeys { it != BackupCatalogCodec.MEMBERSHIPS_PATH }.toMutableMap()
        legacy[BackupCatalogCodec.BOOKS_PATH] =
            Json { encodeDefaults = true; explicitNulls = false }
                .encodeToString(listOf(legacyBook))
                .encodeToByteArray()

        val decoded = decodeWritten(legacy, formatVersion = 1)

        assertThat(decoded.memberships)
            .containsExactly(BackupBookMembershipRecord("book-1", "legacy-group"))
    }

    private fun decodeWritten(catalogs: Map<String, ByteArray>, formatVersion: Int): BackupCatalogSnapshot {
        val root = createTempDirectory("xinyue-catalog").toFile()
        return try {
            val files = catalogs.mapValues { (path, bytes) ->
                File(root, path).also {
                    it.parentFile?.mkdirs()
                    it.writeBytes(bytes)
                }
            }
            BackupCatalogCodec.decode(files, formatVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun book(seriesName: String?, seriesOrder: Int?) = BackupBookRecord(
        id = "book-1",
        title = "公开书名",
        originalFileName = "public.txt",
        charsetName = "UTF-8",
        contentSha256 = "a".repeat(64),
        contentLength = 100,
        createdAtEpochMillis = 1,
        seriesName = seriesName,
        seriesOrder = seriesOrder,
    )

    private fun group(id: String) = BackupGroupRecord(id, id, 0, 1, 1)
}
