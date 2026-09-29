package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreAssetAction
import com.xinyue.reader.core.domain.model.RestoreConflict
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictKind
import com.xinyue.reader.core.domain.model.RestoreEntityAction
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import com.xinyue.reader.core.domain.model.RestorePlan
import com.xinyue.reader.core.domain.model.RestorePreview
import java.util.Locale

class RestorePlanner(
    private val annotationConflictTargetFactory: ((BackupAnnotationRecord, String) -> String)? = null,
    private val idFactory: () -> String,
) {
    fun plan(
        stagedPlanToken: String,
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        manifest: BackupManifest,
        stagedAssetPaths: Set<String>,
        stagingBytes: Long,
        currentSnapshotBytes: Long,
    ): RestorePlan {
        require(stagedPlanToken.isNotBlank() && stagingBytes >= 0 && currentSnapshotBytes >= 0) { "恢复计划输入无效" }
        validateCatalog(backup, manifest)
        val manifestByPath = manifest.entries.associateBy { it.path }
        validateAssetReferences(backup, manifest, manifestByPath, stagedAssetPaths)
        val actions = mutableListOf<RestoreEntityAction>()
        val assets = mutableListOf<RestoreAssetAction>()
        val conflicts = mutableListOf<RestoreConflict>()

        val groupIdRemap = planGroups(backup, current, actions, conflicts)
        val themeIdRemap = planThemes(backup, current, actions, conflicts)
        val fontIdRemap = planFonts(backup, current, manifestByPath, stagedAssetPaths, actions, assets)
        val bookIdRemap = planBooks(
            backup, current, manifest, manifestByPath, stagedAssetPaths, groupIdRemap, actions, assets, conflicts,
        )
        planProgress(backup, current, bookIdRemap, actions, conflicts)
        planAnnotations(backup, current, bookIdRemap, actions, conflicts)
        planSettings(backup, current, bookIdRemap, actions, conflicts)
        planStatistics(backup, current, bookIdRemap, actions, conflicts)

        require(assets.map { it.targetRelativePath }.toSet().size == assets.size) { "恢复资产目标重复" }
        val sortedActions = actions.sortedWith(compareBy({ it.kind.name }, { it.sourceId }, { it.targetId }))
        val sortedAssets = assets.sortedBy { it.targetRelativePath }
        val sortedConflicts = conflicts.distinctBy { it.id }.sortedBy { it.id }
        val bookActions = sortedActions.filter { it.kind == RestoreEntityKind.BOOK }
        val publishBytes = sortedAssets.sumOf { it.sizeBytes }
        val baseRequired = safeAdd(stagingBytes, publishBytes, currentSnapshotBytes)
        val margin = maxOf(MIN_FREE_SPACE_MARGIN, baseRequired / 10)
        val preview = RestorePreview(
            stagedPlanToken = stagedPlanToken,
            formatVersion = manifest.formatVersion,
            createdAtEpochMillis = manifest.createdAtEpochMillis,
            options = manifest.options,
            newBookCount = bookActions.count { it.action in NEW_BOOK_ACTIONS },
            duplicateBookCount = bookActions.count { it.action == RestoreActionKind.SKIP_IDENTICAL },
            conflictCount = sortedConflicts.size,
            stagingBytes = stagingBytes,
            publishBytes = publishBytes,
            snapshotBytes = currentSnapshotBytes,
            requiredFreeBytes = Math.addExact(baseRequired, margin),
            conflicts = sortedConflicts,
        )
        return RestorePlan(
            stagedPlanToken,
            bookIdRemap.toSortedMap(),
            groupIdRemap.toSortedMap(),
            themeIdRemap.toSortedMap(),
            fontIdRemap.toSortedMap(),
            sortedActions,
            sortedAssets,
            sortedConflicts,
            preview,
        )
    }

    private fun planGroups(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ): Map<String, String> {
        val currentByName = current.groups.sortedBy { it.id }.associateBy { it.name.caseKey() }
        val currentById = current.groups.associateBy { it.id }
        val allocator = IdAllocator(current.groups.map { it.id } + backup.groups.map { it.id }, idFactory)
        return backup.groups.sortedBy { it.id }.associate { incoming ->
            val named = currentByName[incoming.name.caseKey()]
            val sameId = currentById[incoming.id]
            val target = named?.id ?: if (sameId == null) incoming.id else allocator.next()
            val action = when {
                sameId == incoming -> RestoreActionKind.SKIP_IDENTICAL
                named != null -> RestoreActionKind.KEEP_LOCAL
                sameId != null -> RestoreActionKind.COPY_AS_NEW
                else -> RestoreActionKind.INSERT
            }
            actions += RestoreEntityAction(RestoreEntityKind.GROUP, incoming.id, target, action)
            if (named != null && sameId != incoming || sameId != null && sameId != incoming) {
                conflicts += namedConflict("group:${incoming.id}", RestoreConflictKind.GROUP, incoming.name)
            }
            incoming.id to target
        }
    }

    private fun planThemes(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ): Map<String, String> {
        val currentByName = current.themes.sortedBy { it.id }.associateBy { it.name.caseKey() }
        val currentById = current.themes.associateBy { it.id }
        val allocator = IdAllocator(current.themes.map { it.id } + backup.themes.map { it.id }, idFactory)
        return backup.themes.sortedBy { it.id }.associate { incoming ->
            val named = currentByName[incoming.name.caseKey()]
            val sameId = currentById[incoming.id]
            val target = named?.id ?: if (sameId == null) incoming.id else allocator.next()
            val action = when {
                sameId == incoming -> RestoreActionKind.SKIP_IDENTICAL
                named != null -> RestoreActionKind.KEEP_LOCAL
                sameId != null -> RestoreActionKind.COPY_AS_NEW
                else -> RestoreActionKind.INSERT
            }
            actions += RestoreEntityAction(RestoreEntityKind.THEME, incoming.id, target, action)
            if (named != null && sameId != incoming || sameId != null && sameId != incoming) {
                conflicts += namedConflict("theme:${incoming.id}", RestoreConflictKind.THEME, incoming.name)
            }
            incoming.id to target
        }
    }

    private fun planFonts(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        manifestByPath: Map<String, com.xinyue.reader.core.domain.model.BackupManifestEntry>,
        stagedAssetPaths: Set<String>,
        actions: MutableList<RestoreEntityAction>,
        assets: MutableList<RestoreAssetAction>,
    ): Map<String, String> {
        val currentByHash = current.fonts.sortedBy { it.id }.associateBy { it.contentSha256 }
        val currentIds = current.fonts.map { it.id }.toSet()
        val allocator = IdAllocator(currentIds + backup.fonts.map { it.id }, idFactory)
        val incomingHashTargets = mutableMapOf<String, String>()
        return backup.fonts.sortedBy { it.id }.associate { incoming ->
            val existing = currentByHash[incoming.contentSha256]
            val duplicateTarget = incomingHashTargets[incoming.contentSha256]
            val target = existing?.id ?: duplicateTarget ?: if (incoming.id !in currentIds) incoming.id else allocator.next()
            val action = if (existing != null || duplicateTarget != null) RestoreActionKind.SKIP_IDENTICAL
            else if (target == incoming.id) RestoreActionKind.INSERT else RestoreActionKind.COPY_AS_NEW
            incomingHashTargets.putIfAbsent(incoming.contentSha256, target)
            actions += RestoreEntityAction(RestoreEntityKind.FONT, incoming.id, target, action)
            if (action != RestoreActionKind.SKIP_IDENTICAL) {
                val source = requireNotNull(incoming.assetPath) { "完整备份字体缺少资产引用" }
                assets += assetAction(source, "fonts/$target/font.bin", BackupEntryKind.FONT, manifestByPath, stagedAssetPaths)
            }
            incoming.id to target
        }
    }

    private fun planBooks(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        manifest: BackupManifest,
        manifestByPath: Map<String, com.xinyue.reader.core.domain.model.BackupManifestEntry>,
        stagedAssetPaths: Set<String>,
        groupIdRemap: Map<String, String>,
        actions: MutableList<RestoreEntityAction>,
        assets: MutableList<RestoreAssetAction>,
        conflicts: MutableList<RestoreConflict>,
    ): Map<String, String> {
        val currentByHash = current.books.sortedBy { it.id }.groupBy { it.contentSha256 }
        val currentIds = current.books.map { it.id }.toSet()
        val allocator = IdAllocator(currentIds + backup.books.map { it.id }, idFactory)
        val incomingHashTargets = mutableMapOf<String, String>()
        return backup.books.sortedBy { it.id }.associate { incoming ->
            val existing = currentByHash[incoming.contentSha256]?.firstOrNull()
            val duplicateTarget = incomingHashTargets[incoming.contentSha256]
            val collided = existing == null && duplicateTarget == null && incoming.id in currentIds
            val target = existing?.id ?: duplicateTarget ?: if (!collided) incoming.id else allocator.next()
            incomingHashTargets.putIfAbsent(incoming.contentSha256, target)
            val action = when {
                existing != null || duplicateTarget != null -> RestoreActionKind.SKIP_IDENTICAL
                collided -> RestoreActionKind.COPY_AS_NEW
                manifest.options.includeBookText -> RestoreActionKind.INSERT
                else -> RestoreActionKind.SKIP_UNAVAILABLE
            }
            val references = incoming.groupId?.let { mapOf("groupId" to requireNotNull(groupIdRemap[it]) { "书籍引用未知分组" }) }.orEmpty()
            actions += RestoreEntityAction(RestoreEntityKind.BOOK, incoming.id, target, action, references)
            if (collided) conflicts += bookConflict(incoming)
            if (action in NEW_BOOK_ACTIONS) {
                if (manifest.options.includeBookText) {
                    assets += assetAction(requireNotNull(incoming.originalAssetPath), "books/$target/original.txt", BackupEntryKind.ORIGINAL_TEXT, manifestByPath, stagedAssetPaths)
                    assets += assetAction(requireNotNull(incoming.normalizedAssetPath), "books/$target/content.txt", BackupEntryKind.NORMALIZED_TEXT, manifestByPath, stagedAssetPaths)
                    assets += assetAction(requireNotNull(incoming.offsetIndexAssetPath), "books/$target/offsets.xidx", BackupEntryKind.OFFSET_INDEX, manifestByPath, stagedAssetPaths)
                }
                incoming.customCoverAssetPath?.let { source ->
                    assets += assetAction(source, "books/$target/cover.webp", BackupEntryKind.CUSTOM_COVER, manifestByPath, stagedAssetPaths)
                }
            }
            incoming.id to target
        }
    }

    private fun planProgress(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        bookIdRemap: Map<String, String>,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ) {
        val currentByBook = current.progress.associateBy { it.bookId }
        val grouped = backup.progress.groupBy { incoming ->
            requireNotNull(bookIdRemap[incoming.bookId]) { "进度引用未知书籍" }
        }
        grouped.toSortedMap().forEach { (target, candidates) ->
            val ordered = candidates.sortedWith(compareByDescending<BackupProgressRecord> { it.updatedAtEpochMillis }.thenBy { it.bookId })
            val incoming = ordered.first()
            if (ordered.drop(1).any { it.updatedAtEpochMillis == incoming.updatedAtEpochMillis && !it.sameAnchor(incoming) }) {
                conflicts += choiceConflict("progress-backup:$target", RestoreConflictKind.PROGRESS, target)
            }
            val local = currentByBook[target]
            val action = when {
                local == null -> RestoreActionKind.INSERT
                incoming.updatedAtEpochMillis > local.updatedAtEpochMillis -> RestoreActionKind.REPLACE
                incoming.updatedAtEpochMillis < local.updatedAtEpochMillis -> RestoreActionKind.KEEP_LOCAL
                incoming.sameAnchor(local) -> RestoreActionKind.SKIP_IDENTICAL
                else -> RestoreActionKind.KEEP_LOCAL
            }
            if (local != null && incoming.updatedAtEpochMillis == local.updatedAtEpochMillis && !incoming.sameAnchor(local)) {
                conflicts += choiceConflict("progress:$target", RestoreConflictKind.PROGRESS, target)
            }
            actions += RestoreEntityAction(RestoreEntityKind.PROGRESS, incoming.bookId, target, action, mapOf("bookId" to target))
        }
    }

    private fun planAnnotations(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        bookIdRemap: Map<String, String>,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ) {
        val currentById = current.annotations.associateBy { it.id }
        val allocator = IdAllocator(current.annotations.map { it.id } + backup.annotations.map { it.id }, idFactory)
        backup.annotations.sortedBy { it.id }.forEach { incoming ->
            val targetBook = requireNotNull(bookIdRemap[incoming.bookId]) { "标注引用未知书籍" }
            val local = currentById[incoming.id]
            val identical = local != null && incoming.canonical(targetBook) == local.canonical(local.bookId)
            val deterministicTarget = annotationConflictTargetFactory?.invoke(incoming, targetBook)
            val deterministicExisting = deterministicTarget?.let(currentById::get)
            val deterministicIdentical = deterministicExisting != null &&
                incoming.canonical(targetBook) == deterministicExisting.canonical(deterministicExisting.bookId)
            val action: RestoreActionKind
            val targetId: String
            val conflictCopyOf: String?
            when {
                local == null -> { action = RestoreActionKind.INSERT; targetId = incoming.id; conflictCopyOf = null }
                identical -> { action = RestoreActionKind.SKIP_IDENTICAL; targetId = local.id; conflictCopyOf = null }
                deterministicIdentical -> {
                    action = RestoreActionKind.SKIP_IDENTICAL
                    targetId = requireNotNull(deterministicExisting).id
                    conflictCopyOf = incoming.id
                }
                else -> {
                    action = RestoreActionKind.COPY_AS_NEW
                    targetId = deterministicTarget ?: allocator.next()
                    require(targetId.matches(ID_PATTERN) && targetId !in currentById) {
                        "确定性标注副本 ID 冲突"
                    }
                    conflictCopyOf = incoming.id
                    conflicts += RestoreConflict(
                        "annotation:${incoming.id}", RestoreConflictKind.ANNOTATION, incoming.id,
                        setOf(RestoreConflictChoice.KEEP_BOTH), RestoreConflictChoice.KEEP_BOTH,
                    )
                }
            }
            actions += RestoreEntityAction(
                RestoreEntityKind.ANNOTATION, incoming.id, targetId, action,
                mapOf("bookId" to targetBook), conflictCopyOf,
            )
        }
    }

    private fun planSettings(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        bookIdRemap: Map<String, String>,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ) {
        backup.globalSettings?.let { incoming ->
            val local = current.globalSettings
            val action = when { local == null -> RestoreActionKind.INSERT; local == incoming -> RestoreActionKind.SKIP_IDENTICAL; else -> RestoreActionKind.KEEP_LOCAL }
            if (local != null && local != incoming) conflicts += choiceConflict("settings:global", RestoreConflictKind.GLOBAL_SETTINGS, "全局阅读设置")
            actions += RestoreEntityAction(RestoreEntityKind.GLOBAL_SETTINGS, "global", "global", action)
        }
        val currentByBook = current.bookSettings.associateBy { it.bookId }
        backup.bookSettings.sortedBy { it.bookId }.forEach { incoming ->
            val target = requireNotNull(bookIdRemap[incoming.bookId]) { "单书设置引用未知书籍" }
            val local = currentByBook[target]
            val action = when { local == null -> RestoreActionKind.INSERT; local.overridesJson == incoming.overridesJson -> RestoreActionKind.SKIP_IDENTICAL; else -> RestoreActionKind.KEEP_LOCAL }
            if (local != null && local.overridesJson != incoming.overridesJson) {
                conflicts += choiceConflict("book-settings:$target", RestoreConflictKind.BOOK_SETTINGS, target)
            }
            actions += RestoreEntityAction(RestoreEntityKind.BOOK_SETTINGS, incoming.bookId, target, action, mapOf("bookId" to target))
        }
    }

    private fun planStatistics(
        backup: BackupCatalogSnapshot,
        current: BackupCatalogSnapshot,
        bookIdRemap: Map<String, String>,
        actions: MutableList<RestoreEntityAction>,
        conflicts: MutableList<RestoreConflict>,
    ) {
        val currentSessions = current.sessions.associateBy { it.id }
        backup.sessions.sortedBy { it.id }.forEach { incoming ->
            val targetBook = requireNotNull(bookIdRemap[incoming.bookId]) { "阅读会话引用未知书籍" }
            val local = currentSessions[incoming.id]
            val identical = local != null && incoming.copy(bookId = targetBook) == local
            val action = when { local == null -> RestoreActionKind.INSERT; identical -> RestoreActionKind.SKIP_IDENTICAL; else -> RestoreActionKind.KEEP_LOCAL }
            if (local != null && !identical) conflicts += choiceConflict("session:${incoming.id}", RestoreConflictKind.STATISTICS, incoming.id)
            actions += RestoreEntityAction(RestoreEntityKind.SESSION, incoming.id, incoming.id, action, mapOf("bookId" to targetBook))
        }
        val currentDaily = current.dailyStats.associateBy { it.bookId to it.localEpochDay }
        backup.dailyStats.sortedWith(compareBy({ it.bookId }, { it.localEpochDay })).forEach { incoming ->
            val targetBook = requireNotNull(bookIdRemap[incoming.bookId]) { "每日统计引用未知书籍" }
            val targetId = "$targetBook:${incoming.localEpochDay}"
            val local = currentDaily[targetBook to incoming.localEpochDay]
            val identical = local != null && incoming.copy(bookId = targetBook) == local
            val action = when { local == null -> RestoreActionKind.INSERT; identical -> RestoreActionKind.SKIP_IDENTICAL; else -> RestoreActionKind.KEEP_LOCAL }
            if (local != null && !identical) conflicts += choiceConflict("daily:$targetId", RestoreConflictKind.STATISTICS, targetId)
            actions += RestoreEntityAction(RestoreEntityKind.DAILY_STAT, "${incoming.bookId}:${incoming.localEpochDay}", targetId, action, mapOf("bookId" to targetBook))
        }
    }

    private fun validateCatalog(backup: BackupCatalogSnapshot, manifest: BackupManifest) {
        require(backup.memberships.toSet().size == backup.memberships.size) { "备份集合成员关系重复" }
        require(backup.books.all {
            (it.seriesName == null || it.seriesName.isNotBlank()) &&
                (it.seriesOrder == null || it.seriesOrder in 0..9_999)
        }) { "备份系列信息无效" }
        require(backup.books.map { it.id }.toSet().size == backup.books.size) { "备份书籍 ID 重复" }
        require(backup.groups.map { it.id }.toSet().size == backup.groups.size) { "备份分组 ID 重复" }
        require(backup.themes.map { it.id }.toSet().size == backup.themes.size) { "备份主题 ID 重复" }
        require(backup.fonts.map { it.id }.toSet().size == backup.fonts.size) { "备份字体 ID 重复" }
        require(backup.annotations.map { it.id }.toSet().size == backup.annotations.size) { "备份标注 ID 重复" }
        require(backup.sessions.map { it.id }.toSet().size == backup.sessions.size) { "备份会话 ID 重复" }
        require(backup.progress.map { it.bookId }.toSet().size == backup.progress.size) { "备份进度重复" }
        require(backup.bookSettings.map { it.bookId }.toSet().size == backup.bookSettings.size) { "备份单书设置重复" }
        require(backup.dailyStats.map { it.bookId to it.localEpochDay }.toSet().size == backup.dailyStats.size) { "备份每日统计重复" }
        val bookIds = backup.books.map { it.id }.toSet()
        val groupIds = backup.groups.map { it.id }.toSet()
        require(backup.memberships.all { it.bookId in bookIds && it.groupId in groupIds }) {
            "备份集合成员关系引用未知实体"
        }
        val allIds = sequenceOf(
            backup.books.map { it.id }, backup.groups.map { it.id }, backup.themes.map { it.id },
            backup.fonts.map { it.id }, backup.annotations.map { it.id }, backup.sessions.map { it.id },
        ).flatten()
        require(allIds.all { it.matches(ID_PATTERN) }) { "备份实体 ID 无效" }
        require(backup.books.all { it.contentSha256.matches(SHA256_PATTERN) && it.contentLength >= 0 }) { "备份书籍摘要或长度无效" }
        require(backup.fonts.all { it.contentSha256.matches(SHA256_PATTERN) && it.sizeBytes >= 0 }) { "备份字体摘要或长度无效" }
        require(backup.groups.all { it.name.isNotBlank() } && backup.themes.all { it.name.isNotBlank() }) { "备份命名数据无效" }
        require(backup.progress.all { it.offset >= 0 && it.contentLength >= 0 && it.updatedAtEpochMillis >= 0 }) { "备份进度无效" }
        require(backup.annotations.all { it.startOffset >= 0 && it.endOffset >= it.startOffset }) { "备份标注范围无效" }
        require(backup.sessions.all { it.startedAtEpochMillis >= 0 && it.lastInteractionAtEpochMillis >= it.startedAtEpochMillis && it.activeMillis >= 0 }) { "备份阅读会话无效" }
        require(backup.dailyStats.all { it.activeMillis >= 0 && it.sessionCount >= 0 }) { "备份每日统计无效" }
        require(backup.books.all { it.groupId == null || it.groupId in groupIds }) { "书籍引用未知分组" }
        require(backup.progress.all { it.bookId in bookIds }) { "进度引用未知书籍" }
        require(backup.annotations.all { it.bookId in bookIds }) { "标注引用未知书籍" }
        require(backup.bookSettings.all { it.bookId in bookIds }) { "单书设置引用未知书籍" }
        require(backup.sessions.all { it.bookId in bookIds }) { "阅读会话引用未知书籍" }
        require(backup.dailyStats.all { it.bookId in bookIds }) { "每日统计引用未知书籍" }
        if (!manifest.options.includeBookText) {
            require(backup.books.all { it.originalAssetPath == null && it.normalizedAssetPath == null && it.offsetIndexAssetPath == null }) { "元数据备份错误引用正文" }
        }
        if (!manifest.options.includeFonts) require(backup.fonts.isEmpty()) { "备份清单声明未包含字体" }
    }

    private fun validateAssetReferences(
        backup: BackupCatalogSnapshot,
        manifest: BackupManifest,
        manifestByPath: Map<String, com.xinyue.reader.core.domain.model.BackupManifestEntry>,
        stagedAssetPaths: Set<String>,
    ) {
        fun requireAsset(path: String?, kind: BackupEntryKind) {
            val required = requireNotNull(path) { "完整备份缺少资产引用" }
            val entry = requireNotNull(manifestByPath[required]) { "恢复所需资产不在清单中" }
            require(entry.kind == kind && required in stagedAssetPaths) { "恢复所需资产缺失或类型错误" }
        }
        backup.books.forEach { book ->
            if (manifest.options.includeBookText) {
                requireAsset(book.originalAssetPath, BackupEntryKind.ORIGINAL_TEXT)
                requireAsset(book.normalizedAssetPath, BackupEntryKind.NORMALIZED_TEXT)
                requireAsset(book.offsetIndexAssetPath, BackupEntryKind.OFFSET_INDEX)
            }
            book.customCoverAssetPath?.let { requireAsset(it, BackupEntryKind.CUSTOM_COVER) }
        }
        if (manifest.options.includeFonts) backup.fonts.forEach { requireAsset(it.assetPath, BackupEntryKind.FONT) }
    }

    private fun assetAction(
        source: String,
        target: String,
        kind: BackupEntryKind,
        manifestByPath: Map<String, com.xinyue.reader.core.domain.model.BackupManifestEntry>,
        stagedAssetPaths: Set<String>,
    ): RestoreAssetAction {
        val entry = requireNotNull(manifestByPath[source]) { "恢复所需资产不在清单中" }
        require(entry.kind == kind && source in stagedAssetPaths) { "恢复所需资产缺失或类型错误" }
        return RestoreAssetAction(source, target, kind, entry.uncompressedSize, entry.sha256)
    }

    private fun namedConflict(id: String, kind: RestoreConflictKind, label: String) = RestoreConflict(
        id, kind, label,
        setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.USE_BACKUP, RestoreConflictChoice.RENAME_BACKUP),
        RestoreConflictChoice.KEEP_LOCAL,
    )

    private fun choiceConflict(id: String, kind: RestoreConflictKind, label: String) = RestoreConflict(
        id, kind, label,
        setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.USE_BACKUP),
        RestoreConflictChoice.KEEP_LOCAL,
    )

    private fun bookConflict(book: BackupBookRecord) = RestoreConflict(
        "book:${book.id}", RestoreConflictKind.BOOK_ID, book.title,
        setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.USE_BACKUP, RestoreConflictChoice.COPY_AS_NEW),
        RestoreConflictChoice.COPY_AS_NEW,
    )

    private fun safeAdd(vararg values: Long): Long = values.fold(0L, Math::addExact)
    private fun String.caseKey(): String = trim().lowercase(Locale.ROOT)
    private fun BackupProgressRecord.sameAnchor(other: BackupProgressRecord): Boolean =
        offset == other.offset && contextHash == other.contextHash && prefix == other.prefix && suffix == other.suffix && contentLength == other.contentLength
    private fun BackupAnnotationRecord.canonical(mappedBookId: String) = listOf(
        mappedBookId, kind, startOffset, endOffset, prefix, suffix, selectedSha256, color, note,
    )

    private class IdAllocator(ids: Iterable<String>, private val factory: () -> String) {
        private val used = ids.toMutableSet()
        fun next(): String {
            repeat(1_000) {
                val candidate = factory()
                if (candidate.matches(ID_PATTERN) && used.add(candidate)) return candidate
            }
            throw IllegalStateException("无法生成无冲突恢复 ID")
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        const val MIN_FREE_SPACE_MARGIN = 16L * 1024 * 1024
        val NEW_BOOK_ACTIONS = setOf(
            RestoreActionKind.INSERT,
            RestoreActionKind.COPY_AS_NEW,
        )
    }
}
