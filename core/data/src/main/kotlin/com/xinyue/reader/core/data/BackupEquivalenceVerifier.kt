package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class BackupAssetFingerprint(
    val key: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupEquivalenceSnapshot(
    val logicalCatalog: BackupCatalogSnapshot,
    val logicalDigest: String,
    val assets: List<BackupAssetFingerprint>,
    val canonicalDigest: String,
)

data class BackupEquivalenceResult(
    val equivalent: Boolean,
    val mismatches: List<String>,
    val canonicalDigest: String,
)

/** Compares only canonical user data and required assets; no content or absolute path enters results. */
class BackupEquivalenceVerifier(private val privateRoot: File) {
    fun capture(catalog: BackupCatalogSnapshot): BackupEquivalenceSnapshot {
        validateRelationships(catalog)
        val canonical = catalog.semanticCanonical()
        val logicalDigest = BackupCatalogDigest.sha256(canonical)
        val assets = assetReferences(canonical).map { reference ->
            val file = BackupPathPolicy.resolve(privateRoot, reference.relativePath)
            require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath())) { "等价性资产缺失" }
            BackupAssetFingerprint(reference.key, file.length(), sha256(file))
        }.sortedBy { it.key }
        return BackupEquivalenceSnapshot(canonical, logicalDigest, assets, combinedDigest(logicalDigest, assets))
    }

    fun compare(expected: BackupEquivalenceSnapshot, actualCatalog: BackupCatalogSnapshot): BackupEquivalenceResult {
        val mismatches = mutableListOf<String>()
        val canonical = actualCatalog.semanticCanonical()
        val logicalDigest = runCatching {
            validateRelationships(canonical)
            BackupCatalogDigest.sha256(canonical)
        }.getOrElse {
            mismatches += "logical:relationships"
            BackupCatalogDigest.sha256(canonical)
        }
        if (logicalDigest != expected.logicalDigest) {
            mismatches += logicalMismatches(expected.logicalCatalog, canonical)
            if (mismatches.none { it.startsWith("logical:") }) mismatches += "logical:catalog"
        }
        val actualRefs = runCatching { assetReferences(canonical).associateBy { it.key } }.getOrElse {
            mismatches += "logical:asset-references"
            emptyMap()
        }
        val actualAssets = mutableListOf<BackupAssetFingerprint>()
        expected.assets.forEach { expectedAsset ->
            val reference = actualRefs[expectedAsset.key]
            if (reference == null) {
                mismatches += "asset:${expectedAsset.key}"
                return@forEach
            }
            val actual = runCatching {
                val file = BackupPathPolicy.resolve(privateRoot, reference.relativePath)
                require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()))
                BackupAssetFingerprint(reference.key, file.length(), sha256(file))
            }.getOrNull()
            if (actual == null || actual.sizeBytes != expectedAsset.sizeBytes || actual.sha256 != expectedAsset.sha256) {
                mismatches += "asset:${expectedAsset.key}"
            } else {
                actualAssets += actual
            }
        }
        actualRefs.keys.filterNot { key -> expected.assets.any { it.key == key } }.forEach { mismatches += "asset-extra:$it" }
        val digest = combinedDigest(logicalDigest, actualAssets.sortedBy { it.key })
        return BackupEquivalenceResult(mismatches.isEmpty(), mismatches.distinct().sorted(), digest)
    }

    private fun assetReferences(catalog: BackupCatalogSnapshot): List<AssetReference> = buildList {
        val books = catalog.books.associateBy { it.id }
        catalog.bookSources.forEach { source ->
            val book = requireNotNull(books[source.bookId])
            add(AssetReference("book:${source.bookId}:original", source.originalRelativePath))
            add(AssetReference("book:${source.bookId}:normalized", source.normalizedRelativePath))
            add(AssetReference("book:${source.bookId}:offset-index", source.offsetIndexRelativePath))
            if (book.customCoverAssetPath != null) {
                add(AssetReference("book:${source.bookId}:cover", requireNotNull(source.customCoverRelativePath)))
            } else {
                require(source.customCoverRelativePath == null) { "封面目录引用不一致" }
            }
        }
        val fonts = catalog.fonts.associateBy { it.id }
        catalog.fontSources.forEach { source ->
            requireNotNull(fonts[source.fontId])
            add(AssetReference("font:${source.fontId}", source.relativePath))
        }
        require(map { it.key }.distinct().size == size) { "等价性资产键重复" }
    }

    private fun validateRelationships(catalog: BackupCatalogSnapshot) {
        val books = catalog.books.map { it.id }.toSet()
        val groups = catalog.groups.map { it.id }.toSet()
        require(catalog.books.all { it.groupId == null || it.groupId in groups })
        require(catalog.memberships.all { it.bookId in books && it.groupId in groups })
        require(catalog.progress.all { it.bookId in books })
        require(catalog.annotations.all { it.bookId in books })
        require(catalog.bookSettings.all { it.bookId in books })
        require(catalog.sessions.all { it.bookId in books })
        require(catalog.dailyStats.all { it.bookId in books })
        require(catalog.bookSources.map { it.bookId }.toSet() == books)
        require(catalog.fontSources.map { it.fontId }.toSet() == catalog.fonts.map { it.id }.toSet())
    }

    private fun BackupCatalogSnapshot.semanticCanonical(): BackupCatalogSnapshot = canonicalForRestore().copy(
        globalSettings = globalSettings?.let { value ->
            value.copy(
                settingsJson = normalizeJson<ReaderSettings>(value.settingsJson),
                scheduleJson = normalizeJson<StoredReaderThemeSchedule>(value.scheduleJson),
            )
        },
        bookSettings = bookSettings.map { value ->
            value.copy(overridesJson = normalizeJson<ReaderSettingsOverrides>(value.overridesJson))
        }.sortedBy { it.bookId },
        themes = themes.map { value ->
            value.copy(settingsJson = normalizeJson<ReaderSettings>(value.settingsJson))
        }.sortedBy { it.id },
    )

    private inline fun <reified T> normalizeJson(encoded: String): String =
        readerDataJson.encodeToString(readerDataJson.decodeFromString<T>(encoded))

    private fun logicalMismatches(
        expected: BackupCatalogSnapshot,
        actual: BackupCatalogSnapshot,
    ): List<String> = buildList {
        if (expected.books != actual.books) add("logical:books")
        if (expected.groups != actual.groups) add("logical:groups")
        if (expected.memberships != actual.memberships) add("logical:memberships")
        if (expected.progress != actual.progress) add("logical:progress")
        if (expected.annotations != actual.annotations) add("logical:annotations")
        if (expected.globalSettings != actual.globalSettings) add("logical:global-settings")
        if (expected.bookSettings != actual.bookSettings) add("logical:book-settings")
        if (expected.themes != actual.themes) add("logical:themes")
        if (expected.fonts != actual.fonts) add("logical:fonts")
        if (expected.sessions != actual.sessions) add("logical:sessions")
        if (expected.dailyStats != actual.dailyStats) add("logical:daily-stats")
        if (expected.bookSources != actual.bookSources) add("logical:book-sources")
        if (expected.fontSources != actual.fontSources) add("logical:font-sources")
    }

    private fun combinedDigest(logicalDigest: String, assets: List<BackupAssetFingerprint>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(logicalDigest.encodeToByteArray())
        assets.sortedBy { it.key }.forEach { value ->
            digest.update(0)
            digest.update(value.key.encodeToByteArray())
            digest.update(0)
            digest.update(value.sizeBytes.toString().encodeToByteArray())
            digest.update(0)
            digest.update(value.sha256.encodeToByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class AssetReference(val key: String, val relativePath: String)
}
