package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import kotlin.test.assertFailsWith
import org.junit.Test

class BackupManifestCodecTest {
    @Test
    fun `encoding is canonical UTF-8 with defaults and sorted entries`() {
        val encoded = BackupManifestCodec.encode(
            manifest(
                BackupManifestEntry("catalog/themes.json", BackupEntryKind.CATALOG, 2, HASH_B),
                BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, 1, HASH_A),
            ),
        )
        val text = encoded.toString(Charsets.UTF_8)

        assertThat(encoded.take(3)).isNotEqualTo(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        assertThat(text).contains("\"formatVersion\":1")
        assertThat(text).contains("\"includeBookText\":true")
        assertThat(text).contains("\"includeFonts\":true")
        assertThat(text.indexOf("catalog/books.json")).isLessThan(text.indexOf("catalog/themes.json"))
        assertThat(BackupManifestCodec.encode(BackupManifestCodec.decode(encoded))).isEqualTo(encoded)
    }

    @Test
    fun `decoder tolerates unknown keys but rejects a missing required key`() {
        val valid = BackupManifestCodec.encode(manifest()).toString(Charsets.UTF_8)
        val withUnknown = valid.replaceFirst("{", "{\"futureOptional\":true,")

        assertThat(BackupManifestCodec.decode(withUnknown.encodeToByteArray()).appVersion).isEqualTo("0.1.2")
        assertFailsWith<IllegalArgumentException> {
            BackupManifestCodec.decode(valid.replace("\"appVersion\":\"0.1.2\",", "").encodeToByteArray())
        }
    }

    @Test
    fun `decoder rejects unsupported versions duplicate paths and manifest self entry`() {
        listOf(0, 3).forEach { version ->
            assertFailsWith<IllegalArgumentException> {
                BackupManifestCodec.decode(
                    BackupManifestCodec.encodeUnchecked(manifest().copy(formatVersion = version)),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            BackupManifestCodec.encode(
                manifest(
                    BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, 1, HASH_A),
                    BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, 1, HASH_B),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BackupManifestCodec.encode(
                manifest(BackupManifestEntry("manifest.json", BackupEntryKind.CATALOG, 1, HASH_A)),
            )
        }
    }

    @Test
    fun `backup v2 and handoff v1 are distinct supported manifest identities`() {
        val backup = manifest().copy(formatVersion = 2)
        val handoff = manifest().copy(
            archiveType = BackupArchiveType.HANDOFF,
            rootBookId = "book-1",
            options = BackupOptions(includeBookText = false, includeFonts = false),
        )

        assertThat(BackupManifestCodec.decode(BackupManifestCodec.encode(backup)).formatVersion).isEqualTo(2)
        assertThat(BackupManifestCodec.decode(BackupManifestCodec.encode(handoff)).archiveType)
            .isEqualTo(BackupArchiveType.HANDOFF)
        assertFailsWith<IllegalArgumentException> {
            BackupManifestCodec.encode(handoff.copy(rootBookId = null))
        }
        assertFailsWith<IllegalArgumentException> {
            BackupManifestCodec.encode(handoff.copy(options = handoff.options.copy(includeFonts = true)))
        }
    }

    @Test
    fun `decoder rejects invalid hashes paths kinds sizes and non-finite numbers`() {
        val invalidEntries = listOf(
            BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, 1, "xyz"),
            BackupManifestEntry("../catalog/books.json", BackupEntryKind.CATALOG, 1, HASH_A),
            BackupManifestEntry("/catalog/books.json", BackupEntryKind.CATALOG, 1, HASH_A),
            BackupManifestEntry("catalog\\books.json", BackupEntryKind.CATALOG, 1, HASH_A),
            BackupManifestEntry("catalog/./books.json", BackupEntryKind.CATALOG, 1, HASH_A),
            BackupManifestEntry("catalog/books.json", BackupEntryKind.ORIGINAL_TEXT, 1, HASH_A),
            BackupManifestEntry("catalog/books.json", BackupEntryKind.CATALOG, -1, HASH_A),
        )
        invalidEntries.forEach { entry ->
            assertFailsWith<IllegalArgumentException>(entry.toString()) {
                BackupManifestCodec.encode(manifest(entry))
            }
        }

        val valid = BackupManifestCodec.encode(manifest()).toString(Charsets.UTF_8)
        listOf("NaN", "Infinity", "-Infinity").forEach { value ->
            val invalid = valid.replace("\"createdAtEpochMillis\":123", "\"createdAtEpochMillis\":$value")
            assertFailsWith<IllegalArgumentException> { BackupManifestCodec.decode(invalid.encodeToByteArray()) }
        }
    }

    private fun manifest(vararg entries: BackupManifestEntry) = BackupManifest(
        appVersion = "0.1.2",
        createdAtEpochMillis = 123,
        options = BackupOptions(),
        entries = entries.toList(),
    )

    private companion object {
        val HASH_A = "a".repeat(64)
        val HASH_B = "b".repeat(64)
    }
}
