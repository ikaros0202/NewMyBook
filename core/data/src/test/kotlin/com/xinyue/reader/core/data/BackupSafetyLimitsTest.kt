package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import kotlin.test.assertFailsWith
import org.junit.Test

class BackupSafetyLimitsTest {
    @Test
    fun `fixed limits match the approved format one contract`() {
        assertThat(BackupSafetyLimits.MAX_ENTRY_COUNT).isEqualTo(10_000)
        assertThat(BackupSafetyLimits.MAX_TEXT_BYTES).isEqualTo(50L * 1024 * 1024)
        assertThat(BackupSafetyLimits.MAX_FONT_BYTES).isEqualTo(64L * 1024 * 1024)
        assertThat(BackupSafetyLimits.MAX_COVER_BYTES).isEqualTo(20L * 1024 * 1024)
        assertThat(BackupSafetyLimits.MAX_CATALOG_BYTES).isEqualTo(16L * 1024 * 1024)
        assertThat(BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES).isEqualTo(2L * 1024 * 1024 * 1024)
        assertThat(BackupSafetyLimits.MAX_COMPRESSION_RATIO).isEqualTo(200L)
    }

    @Test
    fun `each classified entry accepts its exact boundary and rejects one byte over`() {
        val cases = listOf(
            BackupEntryKind.CATALOG to BackupSafetyLimits.MAX_CATALOG_BYTES,
            BackupEntryKind.ORIGINAL_TEXT to BackupSafetyLimits.MAX_TEXT_BYTES,
            BackupEntryKind.NORMALIZED_TEXT to BackupSafetyLimits.MAX_TEXT_BYTES,
            BackupEntryKind.OFFSET_INDEX to BackupSafetyLimits.MAX_CATALOG_BYTES,
            BackupEntryKind.CUSTOM_COVER to BackupSafetyLimits.MAX_COVER_BYTES,
            BackupEntryKind.FONT to BackupSafetyLimits.MAX_FONT_BYTES,
        )
        cases.forEach { (kind, limit) ->
            BackupSafetyLimits.requireEntrySize(kind, limit)
            assertFailsWith<IllegalArgumentException>(kind.name) {
                BackupSafetyLimits.requireEntrySize(kind, limit + 1)
            }
        }
    }

    @Test
    fun `entry count total expansion and compression ratio are bounded`() {
        BackupSafetyLimits.requireEntryCount(10_000)
        BackupSafetyLimits.requireTotalExpandedBytes(BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES)
        BackupSafetyLimits.requireCompressionRatio(compressedSize = 1, uncompressedSize = 200)

        assertFailsWith<IllegalArgumentException> { BackupSafetyLimits.requireEntryCount(10_001) }
        assertFailsWith<IllegalArgumentException> {
            BackupSafetyLimits.requireTotalExpandedBytes(BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            BackupSafetyLimits.requireCompressionRatio(compressedSize = 1, uncompressedSize = 201)
        }
        assertFailsWith<IllegalArgumentException> {
            BackupSafetyLimits.requireCompressionRatio(compressedSize = 0, uncompressedSize = 1)
        }
    }

    @Test
    fun `manifest validation applies count kind and total limits`() {
        val entries = listOf(
            BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, 10, "a".repeat(64)),
            BackupManifestEntry("assets/books/book-1/original.txt", BackupEntryKind.ORIGINAL_TEXT, 20, "b".repeat(64)),
        )
        BackupSafetyLimits.requireManifestEntries(entries)

        assertFailsWith<IllegalArgumentException> {
            BackupSafetyLimits.requireManifestEntries(
                entries + BackupManifestEntry(
                    "assets/books/book-1/cover.webp",
                    BackupEntryKind.CUSTOM_COVER,
                    BackupSafetyLimits.MAX_COVER_BYTES + 1,
                    "c".repeat(64),
                ),
            )
        }
    }
}
