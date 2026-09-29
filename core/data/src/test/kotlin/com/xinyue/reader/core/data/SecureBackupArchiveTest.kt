package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecureBackupArchiveTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `valid metadata-only archive is copied inspected and extracted exactly`() {
        val archive = archive("valid", validCatalogs())
        val staging = temporaryFolder.newFolder("valid-staging")

        val result = SecureBackupArchive().inspectAndExtract(
            archive.inputStream(), staging, "valid", archive.length(), staging.usableSpace,
        )

        assertThat(result.manifest.options).isEqualTo(BackupOptions(false, false))
        assertThat(result.files.keys).containsExactlyElementsIn(BackupManifestCodec.CATALOG_PATHS)
        result.manifest.entries.forEach { expected ->
            val file = result.files.getValue(expected.path)
            assertThat(file.toPath().normalize().startsWith(result.extractionRoot.toPath().normalize())).isTrue()
            assertThat(file.length()).isEqualTo(expected.uncompressedSize)
            assertThat(sha256(file.readBytes())).isEqualTo(expected.sha256)
        }
    }

    @Test
    fun `ambiguous paths duplicate normalized names and non-files are rejected without staging residue`() {
        val cases = listOf(
            "parent" to listOf(payload("../escape", "x")),
            "absolute" to listOf(payload("/absolute", "x")),
            "drive" to listOf(payload("C:/drive", "x")),
            "backslash" to listOf(payload("catalog\\books.json", "x")),
            "dot" to listOf(payload("catalog/./books.json", "x")),
            "empty-segment" to listOf(payload("catalog//books.json", "x")),
            "control" to listOf(payload("catalog/line\n.json", "x")),
            "unicode-duplicate" to listOf(payload("catalog/caf\u00e9.json", "x"), payload("catalog/cafe\u0301.json", "y")),
            "directory" to listOf(payload("catalog/books.json/", "" , directory = true)),
            "symlink" to listOf(payload("catalog/books.json", "target", unixMode = UnixStat.LINK_FLAG or 0x1FF)),
        )

        cases.forEach { (name, entries) -> assertRejected(name, archive(name, entries)) }
    }

    @Test
    fun `duplicate manifest encrypted and unsupported entries are rejected before extraction`() {
        assertRejected("duplicate-manifest", archive("duplicate-manifest", validCatalogs(), manifestCopies = 2))
        val encrypted = archive("encrypted", validCatalogs())
        patchFirstPayloadFlag(encrypted, 0x0001)
        assertRejected("encrypted", encrypted)
        val unsupported = archive("unsupported", validCatalogs())
        patchFirstPayloadMethod(unsupported, 99)
        assertRejected("unsupported", unsupported)
    }

    @Test
    fun `manifest mismatch future version truncation crc and sha tampering are rejected`() {
        val catalogs = validCatalogs()
        assertRejected("missing-entry", archive("missing-entry", catalogs.dropLast(1), manifestEntries = catalogs))
        assertRejected("extra-entry", archive("extra-entry", catalogs, manifestEntries = catalogs.dropLast(1)))
        assertRejected("future", archive("future", catalogs, formatVersion = 3))
        assertRejected("sha", archive("sha", catalogs, hashOverride = "0".repeat(64)))

        val truncated = archive("truncated", catalogs)
        truncated.writeBytes(truncated.readBytes().dropLast(12).toByteArray())
        assertRejected("truncated", truncated)

        val crc = archive("crc", catalogs)
        corruptFirstPayloadByte(crc)
        assertRejected("crc", crc)
    }

    @Test
    fun `compressed source count and free-space preflight are enforced`() {
        val bytes = ByteArray(5)
        val staging = temporaryFolder.newFolder("bounded-staging")
        val archive = SecureBackupArchive(maxCompressedBytes = 4)

        val counted = runCatching { archive.inspectAndExtract(bytes.inputStream(), staging, "counted", null, Long.MAX_VALUE) }
        val hinted = runCatching { archive.inspectAndExtract(bytes.inputStream(), staging, "hinted", 5, Long.MAX_VALUE) }
        val noSpace = runCatching { SecureBackupArchive().inspectAndExtract(bytes.inputStream(), staging, "space", 5, 4) }

        assertThat(counted.exceptionOrNull()).isNotNull()
        assertThat(hinted.exceptionOrNull()).isNotNull()
        assertThat(noSpace.exceptionOrNull()).isNotNull()
        assertThat(staging.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `entry count per-kind total expansion and ratio limits reject metadata bombs`() {
        val tooManyPayloads = (0..BackupSafetyLimits.MAX_ENTRY_COUNT).map { index ->
            Payload("assets/books/b$index/original.txt", ByteArray(0), BackupEntryKind.ORIGINAL_TEXT)
        }
        assertRejected("entry-count", archive("entry-count", tooManyPayloads))

        val oversized = archive(
            "oversized",
            listOf(Payload("assets/books/book-1/original.txt", byteArrayOf(1), BackupEntryKind.ORIGINAL_TEXT, declaredSize = BackupSafetyLimits.MAX_TEXT_BYTES + 1)),
        )
        patchPayloadSizes(oversized, listOf(BackupSafetyLimits.MAX_TEXT_BYTES + 1))
        assertRejected("oversized", oversized)

        val totalPayloads = (0 until 41).map { index ->
            Payload("assets/books/total-$index/original.txt", byteArrayOf(1), BackupEntryKind.ORIGINAL_TEXT, declaredSize = BackupSafetyLimits.MAX_TEXT_BYTES)
        }
        val total = archive("expanded-total", totalPayloads)
        patchPayloadSizes(total, List(totalPayloads.size) { BackupSafetyLimits.MAX_TEXT_BYTES })
        assertRejected("expanded-total", total)

        val ratioPayload = Payload("catalog/books.json", byteArrayOf(1), declaredSize = 1_000)
        val ratio = archive("ratio", listOf(ratioPayload))
        patchPayloadSizes(ratio, listOf(1_000))
        assertRejected("ratio", ratio)
    }

    @Test
    fun `unknown size and unsupported general-purpose flag are rejected`() {
        val unknownSize = archive("unknown-size", validCatalogs())
        patchPayloadSizes(unknownSize, listOf(0xFFFF_FFFFL))
        assertRejected("unknown-size", unknownSize)

        val unknownFlag = archive("unknown-flag", validCatalogs())
        patchFirstPayloadFlag(unknownFlag, 0x4000)
        assertRejected("unknown-flag", unknownFlag)
    }

    @Test
    fun `hostile archive matrix leaves a known live library byte identical`() {
        val liveRoot = temporaryFolder.newFolder("known-live-library")
        val liveCatalog = File(liveRoot, "catalog.bin").also { it.writeText("known-public-live-state") }
        val before = sha256(liveCatalog.readBytes())
        val catalogs = validCatalogs()

        val entryMutation = archive("isolation-entry-mutation", catalogs)
        corruptFirstPayloadByte(entryMutation)
        val truncated = archive("isolation-truncation", catalogs).also { file ->
            file.writeBytes(file.readBytes().dropLast(12).toByteArray())
        }
        val ratio = archive(
            "isolation-ratio",
            listOf(Payload("catalog/books.json", byteArrayOf(1), declaredSize = 1_000)),
        ).also { patchPayloadSizes(it, listOf(1_000)) }
        val cases = listOf(
            "isolation-manifest-hash" to archive("isolation-manifest-hash", catalogs, hashOverride = "0".repeat(64)),
            "isolation-entry-mutation" to entryMutation,
            "isolation-traversal" to archive("isolation-traversal", listOf(payload("../escape", "x"))),
            "isolation-duplicate" to archive(
                "isolation-duplicate",
                listOf(payload("catalog/books.json", "x"), payload("catalog/books.json", "y")),
            ),
            "isolation-symlink" to archive(
                "isolation-symlink",
                listOf(payload("catalog/books.json", "target", unixMode = UnixStat.LINK_FLAG or 0x1FF)),
            ),
            "isolation-ratio" to ratio,
            "isolation-truncation" to truncated,
            "isolation-future" to archive("isolation-future", catalogs, formatVersion = 3),
        )

        cases.forEach { (name, hostile) ->
            assertRejected(name, hostile)
            assertWithMessage("$name live digest").that(sha256(liveCatalog.readBytes())).isEqualTo(before)
            assertWithMessage("$name live file").that(liveCatalog.isFile).isTrue()
        }
    }

    private fun assertRejected(name: String, archive: File) {
        val staging = temporaryFolder.newFolder("staging-$name")
        val result = runCatching {
            SecureBackupArchive().inspectAndExtract(archive.inputStream(), staging, name, archive.length(), staging.usableSpace)
        }
        assertWithMessage(name).that(result.exceptionOrNull()).isNotNull()
        assertWithMessage("$name residue").that(File(staging, "import-$name").exists()).isFalse()
    }

    private fun validCatalogs(): List<Payload> = BackupManifestCodec.CATALOG_PATHS.sorted().map { path ->
        payload(path, if (path == "catalog/settings.json") "{\"global\":null,\"books\":[]}" else "[]")
    }

    private fun payload(
        path: String,
        text: String,
        directory: Boolean = false,
        unixMode: Int = UnixStat.FILE_FLAG or 0x1A4,
    ) = Payload(path, text.encodeToByteArray(), BackupEntryKind.CATALOG, directory, unixMode)

    private fun archive(
        name: String,
        payloads: List<Payload>,
        manifestEntries: List<Payload> = payloads,
        manifestCopies: Int = 1,
        formatVersion: Int = 1,
        hashOverride: String? = null,
    ): File {
        val file = temporaryFolder.newFile("$name.xinyuebackup")
        val manifest = BackupManifest(
            formatVersion = formatVersion,
            appVersion = "1.2-test",
            createdAtEpochMillis = 1,
            options = BackupOptions(false, false),
            entries = manifestEntries.map {
                BackupManifestEntry(it.path, it.kind, it.declaredSize ?: it.bytes.size.toLong(), hashOverride ?: sha256(it.bytes))
            },
        )
        ZipArchiveOutputStream(file).use { output ->
            repeat(manifestCopies) { writeEntry(output, Payload("manifest.json", BackupManifestCodec.encodeUnchecked(manifest))) }
            payloads.forEach { writeEntry(output, it) }
        }
        return file
    }

    private fun writeEntry(output: ZipArchiveOutputStream, payload: Payload) {
        val entry = ZipArchiveEntry(if (payload.directory && !payload.path.endsWith('/')) payload.path + "/" else payload.path)
        entry.unixMode = payload.unixMode
        entry.method = ZipEntry.DEFLATED
        output.putArchiveEntry(entry)
        output.write(payload.bytes)
        output.closeArchiveEntry()
    }

    private fun patchFirstPayloadMethod(file: File, method: Int) {
        val bytes = file.readBytes()
        patchLittleEndianShort(bytes, findSignature(bytes, CENTRAL_SIGNATURE, 2), 10, method)
        patchLittleEndianShort(bytes, findSignature(bytes, LOCAL_SIGNATURE, 2), 8, method)
        file.writeBytes(bytes)
    }

    private fun patchFirstPayloadFlag(file: File, flag: Int) {
        val bytes = file.readBytes()
        val central = findSignature(bytes, CENTRAL_SIGNATURE, 2)
        val local = findSignature(bytes, LOCAL_SIGNATURE, 2)
        patchLittleEndianShort(bytes, central, 8, littleEndianShort(bytes, central + 8) or flag)
        patchLittleEndianShort(bytes, local, 6, littleEndianShort(bytes, local + 6) or flag)
        file.writeBytes(bytes)
    }

    private fun patchPayloadSizes(file: File, sizes: List<Long>) {
        val bytes = file.readBytes()
        sizes.forEachIndexed { index, size ->
            val central = findSignature(bytes, CENTRAL_SIGNATURE, index + 2)
            patchLittleEndianInt(bytes, central + 24, size)
        }
        file.writeBytes(bytes)
    }

    private fun corruptFirstPayloadByte(file: File) {
        val bytes = file.readBytes()
        val local = findSignature(bytes, LOCAL_SIGNATURE, 2)
        val nameLength = littleEndianShort(bytes, local + 26)
        val extraLength = littleEndianShort(bytes, local + 28)
        bytes[local + 30 + nameLength + extraLength] = (bytes[local + 30 + nameLength + extraLength].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
    }

    private fun findSignature(bytes: ByteArray, signature: ByteArray, occurrence: Int): Int {
        var found = 0
        for (index in 0..bytes.size - signature.size) {
            if (signature.indices.all { bytes[index + it] == signature[it] } && ++found == occurrence) return index
        }
        error("signature not found")
    }

    private fun patchLittleEndianShort(bytes: ByteArray, start: Int, offset: Int, value: Int) {
        bytes[start + offset] = value.toByte()
        bytes[start + offset + 1] = (value ushr 8).toByte()
    }

    private fun patchLittleEndianInt(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Payload(
        val path: String,
        val bytes: ByteArray,
        val kind: BackupEntryKind = BackupEntryKind.CATALOG,
        val directory: Boolean = false,
        val unixMode: Int = UnixStat.FILE_FLAG or 0x1A4,
        val declaredSize: Long? = null,
    )

    private companion object {
        val LOCAL_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val CENTRAL_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
    }
}
