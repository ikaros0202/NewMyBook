package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RestoreEntityKind {
    BOOK,
    GROUP,
    PROGRESS,
    ANNOTATION,
    GLOBAL_SETTINGS,
    BOOK_SETTINGS,
    THEME,
    FONT,
    SESSION,
    DAILY_STAT,
}

@Serializable
enum class RestoreActionKind {
    INSERT,
    INSERT_METADATA_ONLY,
    SKIP_UNAVAILABLE,
    REPLACE,
    KEEP_LOCAL,
    SKIP_IDENTICAL,
    COPY_AS_NEW,
    RENAME_AND_INSERT,
}

@Serializable
data class RestoreEntityAction(
    val kind: RestoreEntityKind,
    val sourceId: String,
    val targetId: String,
    val action: RestoreActionKind,
    val references: Map<String, String> = emptyMap(),
    val conflictCopyOf: String? = null,
)

@Serializable
data class RestoreAssetAction(
    val sourcePath: String,
    val targetRelativePath: String,
    val kind: BackupEntryKind,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class RestorePlan(
    val stagedPlanToken: String,
    val bookIdRemap: Map<String, String>,
    val groupIdRemap: Map<String, String>,
    val themeIdRemap: Map<String, String>,
    val fontIdRemap: Map<String, String>,
    val entityActions: List<RestoreEntityAction>,
    val assetActions: List<RestoreAssetAction>,
    val conflicts: List<RestoreConflict>,
    val preview: RestorePreview,
)
