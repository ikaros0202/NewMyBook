package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import java.text.Normalizer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupManifestCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = false
        allowSpecialFloatingPointValues = false
        prettyPrint = false
    }

    fun encode(manifest: BackupManifest): ByteArray {
        val canonical = canonicalize(manifest)
        validate(canonical)
        return json.encodeToString(canonical).encodeToByteArray()
    }

    fun decode(bytes: ByteArray): BackupManifest = try {
        require(bytes.size.toLong() <= BackupSafetyLimits.MAX_CATALOG_BYTES) { "归档清单过大" }
        require(!bytes.startsWithUtf8Bom()) { "归档清单不能包含 BOM" }
        val decoded = json.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8))
        validate(decoded)
        canonicalize(decoded)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: SerializationException) {
        throw IllegalArgumentException("归档清单格式无效", error)
    }

    internal fun encodeUnchecked(manifest: BackupManifest): ByteArray =
        json.encodeToString(canonicalize(manifest)).encodeToByteArray()

    private fun canonicalize(manifest: BackupManifest): BackupManifest =
        manifest.copy(entries = manifest.entries.sortedBy { it.path })

    private fun validate(manifest: BackupManifest) {
        when (manifest.archiveType) {
            BackupArchiveType.BACKUP -> {
                require(manifest.formatVersion in 1..2) { "不支持的备份格式版本" }
                require(manifest.rootBookId == null) { "完整备份不应指定单书" }
            }
            BackupArchiveType.HANDOFF -> {
                require(manifest.formatVersion == 1) { "不支持的接力包格式版本" }
                require(manifest.rootBookId?.matches(ID_PATTERN) == true) { "接力包书籍 ID 无效" }
                require(!manifest.options.includeFonts) { "接力包不得包含字体" }
            }
        }
        require(manifest.appVersion.isNotBlank() && manifest.appVersion.length <= 100) { "应用版本无效" }
        require(manifest.createdAtEpochMillis >= 0) { "归档创建时间无效" }
        BackupSafetyLimits.requireManifestEntries(manifest.entries)
        val normalizedPaths = HashSet<String>(manifest.entries.size)
        manifest.entries.forEach { entry ->
            validatePathAndKind(entry.path, entry.kind)
            require(entry.sha256.matches(SHA256_PATTERN)) { "归档条目摘要无效" }
            val normalized = Normalizer.normalize(entry.path, Normalizer.Form.NFC)
            require(normalizedPaths.add(normalized)) { "归档条目路径重复" }
        }
    }

    private fun validatePathAndKind(path: String, kind: BackupEntryKind) {
        require(path != MANIFEST_PATH) { "清单不能引用自身" }
        require(path.length in 1..512 && path == Normalizer.normalize(path, Normalizer.Form.NFC)) {
            "归档条目路径无效"
        }
        require(!path.startsWith('/') && '\\' !in path && ':' !in path) { "归档条目路径无效" }
        require(path.none { it.code == 0 || it.isISOControl() }) { "归档条目路径无效" }
        val segments = path.split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) { "归档条目路径无效" }

        val expectedKind = when {
            path in V2_CATALOG_PATHS -> BackupEntryKind.CATALOG
            BOOK_ASSET_PATTERN.matches(path) -> when (path.substringAfterLast('/')) {
                "original.txt" -> BackupEntryKind.ORIGINAL_TEXT
                "content.txt" -> BackupEntryKind.NORMALIZED_TEXT
                "offsets.xidx" -> BackupEntryKind.OFFSET_INDEX
                "cover.webp" -> BackupEntryKind.CUSTOM_COVER
                else -> error("unreachable")
            }
            FONT_ASSET_PATTERN.matches(path) -> BackupEntryKind.FONT
            else -> throw IllegalArgumentException("归档条目路径不在允许目录中")
        }
        require(kind == expectedKind) { "归档条目类型与路径不匹配" }
    }

    private fun ByteArray.startsWithUtf8Bom(): Boolean =
        size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()

    const val MANIFEST_PATH = "manifest.json"
    val V1_CATALOG_PATHS = setOf(
        "catalog/books.json",
        "catalog/groups.json",
        "catalog/progress.json",
        "catalog/annotations.json",
        "catalog/settings.json",
        "catalog/themes.json",
        "catalog/fonts.json",
        "catalog/sessions.json",
        "catalog/daily-stats.json",
    )
    val V2_CATALOG_PATHS = V1_CATALOG_PATHS + "catalog/memberships.json"
    val CATALOG_PATHS = V2_CATALOG_PATHS

    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    private val BOOK_ASSET_PATTERN =
        Regex("assets/books/[A-Za-z0-9_-]{1,128}/(original\\.txt|content\\.txt|offsets\\.xidx|cover\\.webp)")
    private val FONT_ASSET_PATTERN = Regex("assets/fonts/[A-Za-z0-9_-]{1,128}/font\\.bin")
}
