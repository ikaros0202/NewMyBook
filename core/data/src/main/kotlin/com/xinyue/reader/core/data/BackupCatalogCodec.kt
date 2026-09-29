package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object BackupCatalogCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
        allowSpecialFloatingPointValues = false
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: BackupCatalogSnapshot): Map<String, ByteArray> {
        val upgraded = snapshot.withV2Collections()
        return sortedMapOf(
        BOOKS_PATH to json.encodeToString(upgraded.books.sortedBy { it.id }).encodeToByteArray(),
        "catalog/groups.json" to json.encodeToString(upgraded.groups.sortedBy { it.id }).encodeToByteArray(),
        MEMBERSHIPS_PATH to json.encodeToString(
            upgraded.memberships,
        ).encodeToByteArray(),
        "catalog/progress.json" to json.encodeToString(upgraded.progress.sortedBy { it.bookId }).encodeToByteArray(),
        "catalog/annotations.json" to json.encodeToString(upgraded.annotations.sortedBy { it.id }).encodeToByteArray(),
        "catalog/settings.json" to json.encodeToString(
            BackupSettingsCatalog(upgraded.globalSettings, upgraded.bookSettings.sortedBy { it.bookId }),
        ).encodeToByteArray(),
        "catalog/themes.json" to json.encodeToString(upgraded.themes.sortedBy { it.id }).encodeToByteArray(),
        "catalog/fonts.json" to json.encodeToString(upgraded.fonts.sortedBy { it.id }).encodeToByteArray(),
        "catalog/sessions.json" to json.encodeToString(upgraded.sessions.sortedBy { it.id }).encodeToByteArray(),
        "catalog/daily-stats.json" to json.encodeToString(
            upgraded.dailyStats.sortedWith(compareBy({ it.bookId }, { it.localEpochDay })),
        ).encodeToByteArray(),
    ).also { catalogs ->
        require(catalogs.keys == BackupManifestCodec.V2_CATALOG_PATHS) { "备份目录不完整" }
        catalogs.values.forEach { BackupSafetyLimits.requireEntrySize(BackupEntryKind.CATALOG, it.size.toLong()) }
    }
    }

    fun decode(files: Map<String, File>, formatVersion: Int = 2): BackupCatalogSnapshot {
        val requiredPaths = when (formatVersion) {
            1 -> BackupManifestCodec.V1_CATALOG_PATHS
            2 -> BackupManifestCodec.V2_CATALOG_PATHS
            else -> throw IllegalArgumentException("不支持的目录格式版本")
        }
        require(files.keys.containsAll(requiredPaths)) { "备份目录文件不完整" }
        require(files.keys.filter { it.startsWith("catalog/") }.toSet() == requiredPaths) {
            "备份目录包含多余或未知文件"
        }
        fun bytes(path: String): ByteArray = requireNotNull(files[path]).also {
            require(it.isFile && it.length() <= BackupSafetyLimits.MAX_CATALOG_BYTES) { "备份目录文件无效" }
        }.readBytes()

        val books = json.decodeFromString<List<BackupBookRecord>>(bytes(BOOKS_PATH).toString(Charsets.UTF_8))
        val memberships = if (formatVersion == 1) {
            books.mapNotNull { book -> book.groupId?.let { BackupBookMembershipRecord(book.id, it) } }
        } else {
            json.decodeFromString<List<BackupBookMembershipRecord>>(
                bytes(MEMBERSHIPS_PATH).toString(Charsets.UTF_8),
            )
        }
        val settings = json.decodeFromString<BackupSettingsCatalog>(
            bytes("catalog/settings.json").toString(Charsets.UTF_8),
        )
        return BackupCatalogSnapshot(
            books = books.map { it.copy(groupId = null) },
            groups = json.decodeFromString(bytes("catalog/groups.json").toString(Charsets.UTF_8)),
            memberships = memberships,
            progress = json.decodeFromString(bytes("catalog/progress.json").toString(Charsets.UTF_8)),
            annotations = json.decodeFromString(bytes("catalog/annotations.json").toString(Charsets.UTF_8)),
            globalSettings = settings.global,
            bookSettings = settings.books,
            themes = json.decodeFromString(bytes("catalog/themes.json").toString(Charsets.UTF_8)),
            fonts = json.decodeFromString(bytes("catalog/fonts.json").toString(Charsets.UTF_8)),
            sessions = json.decodeFromString(bytes("catalog/sessions.json").toString(Charsets.UTF_8)),
            dailyStats = json.decodeFromString(bytes("catalog/daily-stats.json").toString(Charsets.UTF_8)),
        )
    }

    const val BOOKS_PATH = "catalog/books.json"
    const val MEMBERSHIPS_PATH = "catalog/memberships.json"
}

@Serializable
internal data class BackupSettingsCatalog(
    val global: BackupGlobalSettingsRecord?,
    val books: List<BackupBookSettingsRecord>,
)
