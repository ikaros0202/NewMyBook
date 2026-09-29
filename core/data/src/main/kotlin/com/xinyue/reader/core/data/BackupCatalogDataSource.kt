package com.xinyue.reader.core.data

import kotlinx.serialization.Serializable

fun interface BackupCatalogDataSource {
    suspend fun snapshot(): BackupCatalogSnapshot
}

@Serializable
data class BackupCatalogSnapshot(
    val books: List<BackupBookRecord> = emptyList(),
    val groups: List<BackupGroupRecord> = emptyList(),
    val memberships: List<BackupBookMembershipRecord> = emptyList(),
    val progress: List<BackupProgressRecord> = emptyList(),
    val annotations: List<BackupAnnotationRecord> = emptyList(),
    val globalSettings: BackupGlobalSettingsRecord? = null,
    val bookSettings: List<BackupBookSettingsRecord> = emptyList(),
    val themes: List<BackupThemeRecord> = emptyList(),
    val fonts: List<BackupFontRecord> = emptyList(),
    val sessions: List<BackupSessionRecord> = emptyList(),
    val dailyStats: List<BackupDailyStatRecord> = emptyList(),
    val bookSources: List<BackupBookSource> = emptyList(),
    val fontSources: List<BackupFontSource> = emptyList(),
)

@Serializable
data class BackupBookRecord(
    val id: String,
    val title: String,
    val author: String? = null,
    val originalFileName: String,
    val charsetName: String,
    val contentSha256: String,
    val contentLength: Long,
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long? = null,
    val groupId: String? = null,
    val seriesName: String? = null,
    val seriesOrder: Int? = null,
    val finished: Boolean = false,
    val originalAssetPath: String? = null,
    val normalizedAssetPath: String? = null,
    val offsetIndexAssetPath: String? = null,
    val customCoverAssetPath: String? = null,
)

@Serializable
data class BackupBookMembershipRecord(
    val bookId: String,
    val groupId: String,
)

@Serializable
data class BackupGroupRecord(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupProgressRecord(
    val bookId: String,
    val offset: Long,
    val contextHash: String,
    val prefix: String,
    val suffix: String,
    val contentLength: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupAnnotationRecord(
    val id: String,
    val bookId: String,
    val kind: String,
    val startOffset: Long,
    val endOffset: Long,
    val prefix: String,
    val suffix: String,
    val selectedSha256: String?,
    val color: String?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupGlobalSettingsRecord(
    val settingsJson: String,
    val scheduleJson: String,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupBookSettingsRecord(
    val bookId: String,
    val overridesJson: String,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupThemeRecord(
    val id: String,
    val name: String,
    val settingsJson: String,
    val builtIn: Boolean,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BackupFontRecord(
    val id: String,
    val displayName: String,
    val contentSha256: String,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
    val assetPath: String? = null,
)

@Serializable
data class BackupSessionRecord(
    val id: String,
    val bookId: String,
    val startedAtEpochMillis: Long,
    val lastInteractionAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val activeMillis: Long,
)

@Serializable
data class BackupDailyStatRecord(
    val bookId: String,
    val localEpochDay: Long,
    val activeMillis: Long,
    val sessionCount: Long,
)

@Serializable
data class BackupBookSource(
    val bookId: String,
    val originalRelativePath: String,
    val normalizedRelativePath: String,
    val offsetIndexRelativePath: String,
    val customCoverRelativePath: String?,
)

@Serializable
data class BackupFontSource(val fontId: String, val relativePath: String)

internal fun BackupCatalogSnapshot.withV2Collections(): BackupCatalogSnapshot {
    val upgradedMemberships = (
        memberships + books.mapNotNull { book ->
            book.groupId?.let { BackupBookMembershipRecord(book.id, it) }
        }
        ).distinct().sortedWith(compareBy({ it.bookId }, { it.groupId }))
    return copy(
        books = books.map { it.copy(groupId = null) },
        memberships = upgradedMemberships,
    )
}
