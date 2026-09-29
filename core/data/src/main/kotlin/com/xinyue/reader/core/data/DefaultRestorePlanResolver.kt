package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreAssetAction
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Resolves a persisted, inspected plan without consulting mutable live state. */
@Singleton
class DefaultRestorePlanResolver @Inject constructor() : RestorePlanResolver {
    override fun resolve(prepared: PreparedRestorePlan, request: RestoreRequest): ResolvedRestore {
        require(request.stagedPlanToken == prepared.plan.stagedPlanToken) { "恢复计划 token 不匹配" }
        return when (request.mode) {
            RestoreMode.OVERWRITE -> overwrite(prepared)
            RestoreMode.MERGE -> merge(prepared, request)
        }
    }

    private fun overwrite(prepared: PreparedRestorePlan): ResolvedRestore {
        require(prepared.staged.manifest.options.includeBookText) { "覆盖恢复需要包含正文的完整备份" }
        val backup = prepared.backupCatalog.withV2Collections()
        val fontIds = backup.fonts.map { it.id }.toSet()
        val themeIds = backup.themes.map { it.id }.toSet()
        val desired = backup.copy(
            books = backup.books.map { it.withPrivateAssetReferences() }.sortedBy { it.id },
            globalSettings = backup.globalSettings?.remapJson(fontIds.associateWith { it }, themeIds.associateWith { it }),
            bookSettings = backup.bookSettings.map { it.remapJson(fontIds.associateWith { it }) }.sortedBy { it.bookId },
            themes = backup.themes.map { it.remapJson(fontIds.associateWith { it }) }.sortedBy { it.id },
            bookSources = backup.books.map(::bookSource).sortedBy { it.bookId },
            fontSources = backup.fonts.map { BackupFontSource(it.id, "fonts/${it.id}/font.bin") }.sortedBy { it.fontId },
        ).canonical()
        val assets = completeAssets(prepared, desired.books.map { it.id }.toSet(), desired.fonts.map { it.id }.toSet())
        val desiredTargets = assets.mapTo(mutableSetOf()) { it.targetRelativePath }
        val deletes = liveAssetPaths(prepared.currentCatalog).filterNot { it in desiredTargets }.sorted()
        return ResolvedRestore(
            desiredCatalog = desired,
            assetActions = assets,
            deleteTargetRelativePaths = deletes,
            restoredCount = desired.books.size,
            skippedCount = 0,
            metadataOnlyBookCount = 0,
        )
    }

    private fun merge(prepared: PreparedRestorePlan, request: RestoreRequest): ResolvedRestore {
        val plan = prepared.plan
        val resolutionList = request.resolutions
        require(resolutionList.map { it.conflictId }.toSet().size == resolutionList.size) { "恢复冲突选择重复" }
        val resolutions = resolutionList.associateBy { it.conflictId }
        require(resolutions.keys == plan.conflicts.map { it.id }.toSet()) { "恢复冲突尚未全部选择" }
        plan.conflicts.forEach { conflict ->
            val resolution = resolutions.getValue(conflict.id)
            require(resolution.choice in conflict.allowedChoices) { "恢复冲突选择无效" }
            if (resolution.choice == RestoreConflictChoice.RENAME_BACKUP) {
                require(!resolution.renamedValue.isNullOrBlank()) { "重命名恢复项不能为空" }
            }
        }

        val backup = prepared.backupCatalog.withV2Collections()
        val current = prepared.currentCatalog.withV2Collections()
        val actions = plan.entityActions.groupBy { it.kind to it.sourceId }
        fun action(kind: RestoreEntityKind, sourceId: String) =
            requireNotNull(actions[kind to sourceId]?.singleOrNull()) { "恢复实体动作缺失或重复" }

        val groups = current.groups.associateBy { it.id }.toMutableMap()
        val groupMap = mutableMapOf<String, String>()
        backup.groups.sortedBy { it.id }.forEach { incoming ->
            val planned = action(RestoreEntityKind.GROUP, incoming.id)
            val resolution = resolutions["group:${incoming.id}"]
            val choice = resolution?.choice
            val target = when (choice) {
                RestoreConflictChoice.RENAME_BACKUP -> uniqueId(incoming.id, groups.keys)
                else -> planned.targetId
            }
            groupMap[incoming.id] = target
            when {
                choice == RestoreConflictChoice.KEEP_LOCAL || planned.action == RestoreActionKind.SKIP_IDENTICAL -> Unit
                choice == RestoreConflictChoice.RENAME_BACKUP -> putNamedGroup(groups, incoming.copy(id = target, name = requireNotNull(resolution.renamedValue).trim()))
                planned.action != RestoreActionKind.KEEP_LOCAL || choice == RestoreConflictChoice.USE_BACKUP -> putNamedGroup(groups, incoming.copy(id = target))
            }
        }

        val fonts = current.fonts.associateBy { it.id }.toMutableMap()
        val fontMap = plan.fontIdRemap.toMutableMap()
        val insertedFonts = mutableSetOf<String>()
        backup.fonts.sortedBy { it.id }.forEach { incoming ->
            val planned = action(RestoreEntityKind.FONT, incoming.id)
            if (planned.action != RestoreActionKind.SKIP_IDENTICAL) {
                fonts[planned.targetId] = incoming.copy(
                    id = planned.targetId,
                    assetPath = "assets/fonts/${planned.targetId}/font.bin",
                )
                insertedFonts += incoming.id
            }
        }

        val themes = current.themes.associateBy { it.id }.toMutableMap()
        val themeMap = mutableMapOf<String, String>()
        backup.themes.sortedBy { it.id }.forEach { incoming ->
            val planned = action(RestoreEntityKind.THEME, incoming.id)
            val resolution = resolutions["theme:${incoming.id}"]
            val choice = resolution?.choice
            val target = when (choice) {
                RestoreConflictChoice.RENAME_BACKUP -> uniqueId(incoming.id, themes.keys)
                else -> planned.targetId
            }
            themeMap[incoming.id] = target
            val mapped = incoming.copy(id = target).remapJson(fontMap)
            when {
                choice == RestoreConflictChoice.KEEP_LOCAL || planned.action == RestoreActionKind.SKIP_IDENTICAL -> Unit
                choice == RestoreConflictChoice.RENAME_BACKUP -> putNamedTheme(themes, mapped.copy(name = requireNotNull(resolution.renamedValue).trim()))
                planned.action != RestoreActionKind.KEEP_LOCAL || choice == RestoreConflictChoice.USE_BACKUP -> putNamedTheme(themes, mapped)
            }
        }

        val books = current.books.associateBy { it.id }.toMutableMap()
        val bookSources = current.bookSources.associateBy { it.bookId }.toMutableMap()
        val effectiveBookMap = mutableMapOf<String, String>()
        val skippedSourceBooks = mutableSetOf<String>()
        val publishedBooks = mutableSetOf<String>()
        val replacedBooks = mutableSetOf<String>()
        var metadataOnly = 0
        backup.books.sortedBy { it.id }.forEach { incoming ->
            val planned = action(RestoreEntityKind.BOOK, incoming.id)
            val resolution = resolutions["book:${incoming.id}"]
            val target = when (resolution?.choice) {
                RestoreConflictChoice.USE_BACKUP -> incoming.id
                else -> planned.targetId
            }
            effectiveBookMap[incoming.id] = target
            when {
                resolution?.choice == RestoreConflictChoice.KEEP_LOCAL -> skippedSourceBooks += incoming.id
                planned.action == RestoreActionKind.SKIP_IDENTICAL -> {
                    if (prepared.staged.manifest.archiveType == BackupArchiveType.HANDOFF) {
                        val local = requireNotNull(books[target])
                        val sameSeries = local.seriesName != null &&
                            local.seriesName.equals(incoming.seriesName, ignoreCase = true)
                        books[target] = local.copy(
                            finished = local.finished || incoming.finished,
                            seriesName = local.seriesName ?: incoming.seriesName,
                            seriesOrder = when {
                                local.seriesName == null -> incoming.seriesOrder
                                sameSeries && local.seriesOrder == null -> incoming.seriesOrder
                                else -> local.seriesOrder
                            },
                        )
                    }
                }
                planned.action == RestoreActionKind.SKIP_UNAVAILABLE -> {
                    skippedSourceBooks += incoming.id
                    metadataOnly++
                }
                else -> {
                    if (target in books) replacedBooks += target
                    books[target] = incoming.copy(id = target, groupId = null).withPrivateAssetReferences()
                    bookSources[target] = bookSource(books.getValue(target))
                    publishedBooks += incoming.id
                }
            }
        }

        val memberships = current.memberships.toMutableSet()
        backup.memberships
            .sortedWith(compareBy({ it.bookId }, { it.groupId }))
            .forEach { incoming ->
                if (incoming.bookId in skippedSourceBooks) return@forEach
                memberships += BackupBookMembershipRecord(
                    bookId = requireNotNull(effectiveBookMap[incoming.bookId]),
                    groupId = requireNotNull(groupMap[incoming.groupId]),
                )
            }

        val progress = current.progress.associateBy { it.bookId }.toMutableMap()
        backup.progress.sortedWith(compareBy({ it.bookId }, { it.updatedAtEpochMillis })).forEach { incoming ->
            if (incoming.bookId in skippedSourceBooks) return@forEach
            val planned = actions[RestoreEntityKind.PROGRESS to incoming.bookId]?.singleOrNull() ?: return@forEach
            val target = requireNotNull(effectiveBookMap[incoming.bookId])
            val resolution = resolutions["progress:$target"] ?: resolutions["progress-backup:$target"]
            val shouldUse = when (resolution?.choice) {
                RestoreConflictChoice.USE_BACKUP -> true
                RestoreConflictChoice.KEEP_LOCAL -> false
                else -> planned.action in setOf(RestoreActionKind.INSERT, RestoreActionKind.REPLACE)
            }
            if (shouldUse) progress[target] = incoming.copy(bookId = target)
        }

        val annotations = current.annotations.associateBy { it.id }.toMutableMap()
        backup.annotations.sortedBy { it.id }.forEach { incoming ->
            if (incoming.bookId in skippedSourceBooks) return@forEach
            val planned = action(RestoreEntityKind.ANNOTATION, incoming.id)
            if (planned.action in setOf(RestoreActionKind.INSERT, RestoreActionKind.COPY_AS_NEW)) {
                annotations[planned.targetId] = incoming.copy(
                    id = planned.targetId,
                    bookId = requireNotNull(effectiveBookMap[incoming.bookId]),
                )
            }
        }

        var global = current.globalSettings
        backup.globalSettings?.let { incoming ->
            val planned = action(RestoreEntityKind.GLOBAL_SETTINGS, "global")
            val resolution = resolutions["settings:global"]
            if (resolution?.choice == RestoreConflictChoice.USE_BACKUP || planned.action == RestoreActionKind.INSERT) {
                global = incoming.remapJson(fontMap, themeMap)
            }
        }

        val bookSettings = current.bookSettings.associateBy { it.bookId }.toMutableMap()
        backup.bookSettings.sortedBy { it.bookId }.forEach { incoming ->
            if (incoming.bookId in skippedSourceBooks) return@forEach
            val planned = action(RestoreEntityKind.BOOK_SETTINGS, incoming.bookId)
            val target = requireNotNull(effectiveBookMap[incoming.bookId])
            val resolution = resolutions["book-settings:$target"]
            if (resolution?.choice == RestoreConflictChoice.USE_BACKUP || planned.action == RestoreActionKind.INSERT) {
                bookSettings[target] = incoming.copy(bookId = target).remapJson(fontMap)
            }
        }

        val sessions = current.sessions.associateBy { it.id }.toMutableMap()
        backup.sessions.sortedBy { it.id }.forEach { incoming ->
            if (incoming.bookId in skippedSourceBooks) return@forEach
            val planned = action(RestoreEntityKind.SESSION, incoming.id)
            val resolution = resolutions["session:${incoming.id}"]
            if (resolution?.choice == RestoreConflictChoice.USE_BACKUP || planned.action == RestoreActionKind.INSERT) {
                sessions[incoming.id] = incoming.copy(bookId = requireNotNull(effectiveBookMap[incoming.bookId]))
            }
        }
        val daily = current.dailyStats.associateBy { it.bookId to it.localEpochDay }.toMutableMap()
        backup.dailyStats.sortedWith(compareBy({ it.bookId }, { it.localEpochDay })).forEach { incoming ->
            if (incoming.bookId in skippedSourceBooks) return@forEach
            val target = requireNotNull(effectiveBookMap[incoming.bookId])
            val planned = action(RestoreEntityKind.DAILY_STAT, "${incoming.bookId}:${incoming.localEpochDay}")
            val resolution = resolutions["daily:$target:${incoming.localEpochDay}"]
            if (resolution?.choice == RestoreConflictChoice.USE_BACKUP || planned.action == RestoreActionKind.INSERT) {
                daily[target to incoming.localEpochDay] = incoming.copy(bookId = target)
            }
        }

        val fontSources = current.fontSources.associateBy { it.fontId }.toMutableMap()
        insertedFonts.forEach { sourceId ->
            val target = requireNotNull(fontMap[sourceId])
            fontSources[target] = BackupFontSource(target, "fonts/$target/font.bin")
        }
        val desired = BackupCatalogSnapshot(
            books = books.values.toList(), groups = groups.values.toList(), memberships = memberships.toList(),
            progress = progress.values.toList(),
            annotations = annotations.values.toList(), globalSettings = global, bookSettings = bookSettings.values.toList(),
            themes = themes.values.toList(), fonts = fonts.values.toList(), sessions = sessions.values.toList(),
            dailyStats = daily.values.toList(), bookSources = bookSources.values.toList(),
            fontSources = fontSources.values.toList(),
        ).canonical()
        val assets = mergeAssets(prepared, publishedBooks, insertedFonts, effectiveBookMap, fontMap)
        val deletes = replacedBooks.flatMap { target ->
            val old = current.bookSources.singleOrNull { it.bookId == target }
            val new = desired.bookSources.single { it.bookId == target }
            old?.allPaths().orEmpty().filterNot { it in new.allPaths() }
        }.distinct().sorted()
        return ResolvedRestore(
            desiredCatalog = desired,
            assetActions = assets,
            deleteTargetRelativePaths = deletes,
            restoredCount = publishedBooks.size,
            skippedCount = skippedSourceBooks.size + backup.books.count {
                action(RestoreEntityKind.BOOK, it.id).action == RestoreActionKind.SKIP_IDENTICAL
            },
            metadataOnlyBookCount = metadataOnly,
            conflictCopyCount = plan.entityActions.count {
                it.kind == RestoreEntityKind.ANNOTATION && it.action == RestoreActionKind.COPY_AS_NEW
            },
        )
    }

    private fun completeAssets(
        prepared: PreparedRestorePlan,
        bookIds: Set<String>,
        fontIds: Set<String>,
    ): List<RestoreAssetAction> {
        val assets = mutableListOf<RestoreAssetAction>()
        prepared.backupCatalog.books.filter { it.id in bookIds }.forEach { book ->
            addBookAssets(assets, prepared, book, book.id)
        }
        prepared.backupCatalog.fonts.filter { it.id in fontIds }.forEach { font ->
            val source = requireNotNull(font.assetPath)
            assets += asset(prepared, source, "fonts/${font.id}/font.bin", BackupEntryKind.FONT)
        }
        return assets.sortedBy { it.targetRelativePath }
    }

    private fun mergeAssets(
        prepared: PreparedRestorePlan,
        sourceBooks: Set<String>,
        sourceFonts: Set<String>,
        bookMap: Map<String, String>,
        fontMap: Map<String, String>,
    ): List<RestoreAssetAction> {
        val assets = mutableListOf<RestoreAssetAction>()
        prepared.backupCatalog.books.filter { it.id in sourceBooks }.forEach { book ->
            addBookAssets(assets, prepared, book, requireNotNull(bookMap[book.id]))
        }
        prepared.backupCatalog.fonts.filter { it.id in sourceFonts }.forEach { font ->
            assets += asset(
                prepared, requireNotNull(font.assetPath),
                "fonts/${requireNotNull(fontMap[font.id])}/font.bin", BackupEntryKind.FONT,
            )
        }
        return assets.distinctBy { it.targetRelativePath }.sortedBy { it.targetRelativePath }
    }

    private fun addBookAssets(
        out: MutableList<RestoreAssetAction>,
        prepared: PreparedRestorePlan,
        book: BackupBookRecord,
        targetId: String,
    ) {
        if (prepared.staged.manifest.options.includeBookText) {
            out += asset(prepared, requireNotNull(book.originalAssetPath), "books/$targetId/original.txt", BackupEntryKind.ORIGINAL_TEXT)
            out += asset(prepared, requireNotNull(book.normalizedAssetPath), "books/$targetId/content.txt", BackupEntryKind.NORMALIZED_TEXT)
            out += asset(prepared, requireNotNull(book.offsetIndexAssetPath), "books/$targetId/offsets.xidx", BackupEntryKind.OFFSET_INDEX)
        }
        book.customCoverAssetPath?.let { out += asset(prepared, it, "books/$targetId/cover.webp", BackupEntryKind.CUSTOM_COVER) }
    }

    private fun asset(
        prepared: PreparedRestorePlan,
        source: String,
        target: String,
        kind: BackupEntryKind,
    ): RestoreAssetAction {
        val entry: BackupManifestEntry = requireNotNull(prepared.staged.manifest.entries.singleOrNull { it.path == source })
        require(entry.kind == kind && source in prepared.staged.files) { "恢复资产缺失或类型错误" }
        return RestoreAssetAction(source, target, kind, entry.uncompressedSize, entry.sha256)
    }

    private fun BackupBookRecord.withPrivateAssetReferences(): BackupBookRecord = copy(
        originalAssetPath = "assets/books/$id/original.txt",
        normalizedAssetPath = "assets/books/$id/content.txt",
        offsetIndexAssetPath = "assets/books/$id/offsets.xidx",
        customCoverAssetPath = customCoverAssetPath?.let { "assets/books/$id/cover.webp" },
    )

    private fun bookSource(book: BackupBookRecord) = BackupBookSource(
        book.id, "books/${book.id}/original.txt", "books/${book.id}/content.txt",
        "books/${book.id}/offsets.xidx", book.customCoverAssetPath?.let { "books/${book.id}/cover.webp" },
    )

    private fun BackupCatalogSnapshot.canonical() = copy(
        books = books.sortedBy { it.id }, groups = groups.sortedBy { it.id },
        memberships = memberships.sortedWith(compareBy({ it.bookId }, { it.groupId })),
        progress = progress.sortedBy { it.bookId },
        annotations = annotations.sortedBy { it.id }, bookSettings = bookSettings.sortedBy { it.bookId },
        themes = themes.sortedBy { it.id }, fonts = fonts.sortedBy { it.id }, sessions = sessions.sortedBy { it.id },
        dailyStats = dailyStats.sortedWith(compareBy({ it.bookId }, { it.localEpochDay })),
        bookSources = bookSources.sortedBy { it.bookId }, fontSources = fontSources.sortedBy { it.fontId },
    )

    private fun BackupGlobalSettingsRecord.remapJson(
        fonts: Map<String, String>,
        themes: Map<String, String>,
    ): BackupGlobalSettingsRecord = copy(
        settingsJson = remapSettings(settingsJson, fonts),
        scheduleJson = remapSchedule(scheduleJson, themes),
    )

    private fun BackupBookSettingsRecord.remapJson(fonts: Map<String, String>) = copy(
        overridesJson = remapOverrides(overridesJson, fonts),
    )

    private fun BackupThemeRecord.remapJson(fonts: Map<String, String>) = copy(
        settingsJson = remapSettings(settingsJson, fonts),
    )

    private fun remapSettings(encoded: String, fonts: Map<String, String>): String {
        val decoded = readerDataJson.decodeFromString<ReaderSettings>(encoded)
        return readerDataJson.encodeToString(decoded.copy(font = decoded.font.remap(fonts)))
    }

    private fun remapOverrides(encoded: String, fonts: Map<String, String>): String {
        val decoded = readerDataJson.decodeFromString<ReaderSettingsOverrides>(encoded)
        return readerDataJson.encodeToString(decoded.copy(font = decoded.font?.remap(fonts)))
    }

    private fun remapSchedule(encoded: String, themes: Map<String, String>): String {
        val decoded = readerDataJson.decodeFromString<StoredReaderThemeSchedule>(encoded)
        val schedule = decoded.schedule.copy(
            lightThemeId = themes[decoded.schedule.lightThemeId] ?: decoded.schedule.lightThemeId,
            darkThemeId = themes[decoded.schedule.darkThemeId] ?: decoded.schedule.darkThemeId,
        )
        val manual = decoded.manualOverride?.let {
            ReaderThemeManualOverride(
                themeId = themes[it.themeId] ?: it.themeId,
                automaticThemeIdAtActivation = themes[it.automaticThemeIdAtActivation] ?: it.automaticThemeIdAtActivation,
                expiresAtEpochMillis = it.expiresAtEpochMillis,
            )
        }
        return readerDataJson.encodeToString(StoredReaderThemeSchedule(schedule, manual))
    }

    private fun ReaderFontRef.remap(fonts: Map<String, String>): ReaderFontRef = when (this) {
        is ReaderFontRef.Imported -> ReaderFontRef.Imported(requireNotNull(fonts[fontId]) { "设置引用未知字体" })
        else -> this
    }

    private fun putNamedGroup(target: MutableMap<String, BackupGroupRecord>, value: BackupGroupRecord) {
        require(target.values.none { it.id != value.id && it.name.caseKey() == value.name.caseKey() }) { "恢复分组名称冲突" }
        target[value.id] = value
    }

    private fun putNamedTheme(target: MutableMap<String, BackupThemeRecord>, value: BackupThemeRecord) {
        require(target.values.none { it.id != value.id && it.name.caseKey() == value.name.caseKey() }) { "恢复主题名称冲突" }
        target[value.id] = value
    }

    private fun uniqueId(source: String, used: Set<String>): String {
        if (source !in used) return source
        val stem = source.take(112)
        for (index in 1..9999) {
            val candidate = "$stem-restore-$index"
            if (candidate !in used) return candidate
        }
        throw IllegalArgumentException("无法生成恢复副本 ID")
    }

    private fun liveAssetPaths(catalog: BackupCatalogSnapshot): Set<String> = buildSet {
        catalog.bookSources.forEach { addAll(it.allPaths()) }
        catalog.fontSources.forEach { add(it.relativePath) }
    }

    private fun BackupBookSource.allPaths(): List<String> = listOfNotNull(
        originalRelativePath, normalizedRelativePath, offsetIndexRelativePath, customCoverRelativePath,
    )

    private fun String.caseKey() = trim().lowercase(Locale.ROOT)

    @Suppress("unused")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
