package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.HandoffPreview
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import com.xinyue.reader.core.domain.model.RestorePlan
import java.security.MessageDigest
import java.util.UUID

/**
 * Applies the narrow one-book handoff policy before delegating to the proven restore planner.
 */
class HandoffPlanner(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val restorePlanner = RestorePlanner(
        annotationConflictTargetFactory = ::deterministicAnnotationCopyId,
        idFactory = idFactory,
    )

    fun plan(
        stagedPlanToken: String,
        incoming: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        manifest: BackupManifest,
        stagedAssetPaths: Set<String>,
        stagingBytes: Long,
        currentSnapshotBytes: Long,
    ): RestorePlan {
        validateArchiveManifest(manifest)
        validateHandoff(incoming, manifest)
        val sourceBook = incoming.books.single()
        if (!manifest.options.includeBookText) {
            require(current.books.any { it.contentSha256 == sourceBook.contentSha256 }) {
                "不含正文的接力包只能应用到内容摘要完全一致的现有书籍"
            }
        }
        return restorePlanner.plan(
            stagedPlanToken = stagedPlanToken,
            backup = incoming,
            current = current,
            manifest = manifest,
            stagedAssetPaths = stagedAssetPaths,
            stagingBytes = stagingBytes,
            currentSnapshotBytes = currentSnapshotBytes,
        )
    }

    fun validateArchiveManifest(manifest: BackupManifest) {
        require(manifest.archiveType == BackupArchiveType.HANDOFF && manifest.formatVersion == 1) {
            "所选文件不是受支持的接力包"
        }
        require(!manifest.options.includeFonts) { "接力包不得包含字体" }
        val rootBookId = requireNotNull(manifest.rootBookId) { "接力包缺少根书籍身份" }
        val catalogPaths = manifest.entries.filter { it.kind == BackupEntryKind.CATALOG }.map { it.path }.toSet()
        require(catalogPaths == BackupManifestCodec.V2_CATALOG_PATHS) { "接力包目录文件不完整或包含多余目录" }
        val assetEntries = manifest.entries.filter { it.kind != BackupEntryKind.CATALOG }
        val expectedAssets = if (manifest.options.includeBookText) {
            mapOf(
                "assets/books/$rootBookId/original.txt" to BackupEntryKind.ORIGINAL_TEXT,
                "assets/books/$rootBookId/content.txt" to BackupEntryKind.NORMALIZED_TEXT,
                "assets/books/$rootBookId/offsets.xidx" to BackupEntryKind.OFFSET_INDEX,
            )
        } else {
            emptyMap()
        }
        require(assetEntries.associate { it.path to it.kind } == expectedAssets) {
            "接力包正文资产不完整或包含禁止资产"
        }
    }

    fun preview(
        plan: RestorePlan,
        incoming: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        manifest: BackupManifest,
    ): HandoffPreview {
        val sourceBook = incoming.books.single()
        val targetBookId = plan.bookIdRemap[sourceBook.id]
        val bookAction = plan.entityActions.single {
            it.kind == RestoreEntityKind.BOOK && it.sourceId == sourceBook.id
        }
        val incomingProgress = incoming.progress.singleOrNull()
        val localProgress = targetBookId?.let { target -> current.progress.singleOrNull { it.bookId == target } }
        return HandoffPreview(
            stagedPlanToken = plan.stagedPlanToken,
            formatVersion = manifest.formatVersion,
            createdAtEpochMillis = manifest.createdAtEpochMillis,
            sourceBookId = sourceBook.id,
            targetBookId = targetBookId,
            title = sourceBook.title,
            includesBookText = manifest.options.includeBookText,
            importsNewBook = bookAction.action in setOf(RestoreActionKind.INSERT, RestoreActionKind.COPY_AS_NEW),
            incomingProgressIsNewer = incomingProgress != null &&
                (localProgress == null || incomingProgress.updatedAtEpochMillis > localProgress.updatedAtEpochMillis),
            annotationInsertCount = plan.entityActions.count {
                it.kind == RestoreEntityKind.ANNOTATION && it.action == RestoreActionKind.INSERT
            },
            annotationConflictCopyCount = plan.entityActions.count {
                it.kind == RestoreEntityKind.ANNOTATION && it.action == RestoreActionKind.COPY_AS_NEW
            },
            collectionCount = incoming.memberships.size,
            conflicts = plan.conflicts,
        )
    }

    private fun validateHandoff(incoming: BackupCatalogSnapshot, manifest: BackupManifest) {
        require(incoming.books.size == 1) { "接力包必须且只能包含一本书" }
        val book = incoming.books.single()
        require(manifest.rootBookId == book.id) { "接力包根书籍身份不一致" }
        require(
            incoming.globalSettings == null &&
                incoming.themes.isEmpty() &&
                incoming.fonts.isEmpty() &&
                incoming.sessions.isEmpty() &&
                incoming.dailyStats.isEmpty() &&
                incoming.fontSources.isEmpty(),
        ) { "接力包包含禁止的全局或其他书籍数据" }
        require(incoming.progress.all { it.bookId == book.id }) { "接力包进度引用了其他书籍" }
        require(incoming.annotations.all { it.bookId == book.id }) { "接力包标注引用了其他书籍" }
        require(incoming.bookSettings.all { it.bookId == book.id }) { "接力包设置引用了其他书籍" }
        require(incoming.memberships.all { it.bookId == book.id }) { "接力包集合关系引用了其他书籍" }
        val referencedGroups = incoming.memberships.map { it.groupId }.toSet()
        require(incoming.groups.map { it.id }.toSet() == referencedGroups) { "接力包集合目录与成员关系不一致" }
        require(book.customCoverAssetPath == null) { "接力包不得包含自定义封面" }
    }

    private fun deterministicAnnotationCopyId(
        annotation: BackupAnnotationRecord,
        targetBookId: String,
    ): String {
        val canonical = listOf(
            annotation.id,
            targetBookId,
            annotation.kind,
            annotation.startOffset,
            annotation.endOffset,
            annotation.prefix,
            annotation.suffix,
            annotation.selectedSha256,
            annotation.color,
            annotation.note,
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
        return "handoff-" + digest.joinToString("") { "%02x".format(it) }.take(48)
    }
}
