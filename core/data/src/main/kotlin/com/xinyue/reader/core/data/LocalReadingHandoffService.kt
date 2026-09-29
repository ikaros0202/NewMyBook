package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.domain.model.BackupError
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportResult
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import com.xinyue.reader.core.domain.model.HandoffImportResult
import com.xinyue.reader.core.domain.model.HandoffInspectResult
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.RestoreActionKind
import com.xinyue.reader.core.domain.model.RestoreEntityKind
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Singleton
class LocalReadingHandoffService internal constructor(
    private val catalogDataSource: BackupCatalogDataSource,
    private val assetInventory: BackupAssetInventory,
    private val archiveWriter: SecureBackupWriter,
    private val documentGateway: BackupDocumentGateway,
    private val privateRoot: File,
    private val appVersion: String,
    private val nowEpochMillis: () -> Long,
    private val idFactory: () -> String,
    private val archiveReader: SecureBackupArchive = SecureBackupArchive(),
    private val planner: HandoffPlanner = HandoffPlanner(),
    private val stagedPlans: StagedRestorePlanRegistry = StagedRestorePlanRegistry(),
    private val restorePublisher: RestorePublisher? = null,
    private val restoreRecovery: RestoreRecovery? = null,
) : ReadingHandoffService {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        catalogDataSource: RoomBackupCatalogDataSource,
        documentGateway: AndroidBackupDocumentGateway,
        stagedPlans: StagedRestorePlanRegistry,
        restorePublisher: RestorePublisher,
        restoreRecovery: RestoreRecovery,
    ) : this(
        catalogDataSource = catalogDataSource,
        assetInventory = BackupAssetInventory(context.filesDir),
        archiveWriter = SecureBackupWriter(BackupAssetInventory(context.filesDir)),
        documentGateway = documentGateway,
        privateRoot = context.filesDir,
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown",
        nowEpochMillis = System::currentTimeMillis,
        idFactory = { UUID.randomUUID().toString() },
        planner = HandoffPlanner(),
        stagedPlans = stagedPlans,
        restorePublisher = restorePublisher,
        restoreRecovery = restoreRecovery,
    )

    override suspend fun listBooks(): List<HandoffBookSummary> = withContext(Dispatchers.IO) {
        catalogDataSource.snapshot().books
            .sortedWith(compareByDescending<BackupBookRecord> { it.lastOpenedAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.title }
                .thenBy { it.id })
            .map { HandoffBookSummary(it.id, it.title, it.author, it.seriesName) }
    }

    override suspend fun export(
        destinationUri: String,
        request: HandoffExportRequest,
        onProgress: (BackupProgress) -> Unit,
    ): HandoffExportResult = withContext(Dispatchers.IO) {
        val operationId = idFactory()
        val stagingRoot = File(privateRoot, "handoff-staging").apply { mkdirs() }
        val temporary = File(stagingRoot, "export-$operationId.tmp")
        var destinationAttempted = false
        try {
            require(request.bookId.matches(ID_PATTERN)) { "接力书籍 ID 无效" }
            check(temporary.createNewFile()) { "无法创建接力临时文件" }
            onProgress(BackupProgress(BackupPhase.PREPARING, 0, 1, "preparing-handoff"))
            val full = catalogDataSource.snapshot()
            val handoff = full.singleBookHandoff(request.bookId, request.includeBookText)
            val options = BackupOptions(includeBookText = request.includeBookText, includeFonts = false)
            val inventory = assetInventory.collect(handoff, options)
            val written = archiveWriter.writeHandoff(
                archive = temporary,
                catalog = inventory.catalog,
                assets = inventory.assets,
                options = options,
                appVersion = appVersion,
                createdAtEpochMillis = nowEpochMillis(),
                rootBookId = request.bookId,
                onProgress = onProgress,
            )
            currentCoroutineContext().ensureActive()
            destinationAttempted = true
            documentGateway.openForWrite(destinationUri).use { output ->
                temporary.inputStream().buffered().use { input -> input.copyTo(output) }
                output.flush()
            }
            HandoffExportResult.Success(operationId, written.archiveSha256, written.bytesWritten)
        } catch (_: CancellationException) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            HandoffExportResult.Cancelled
        } catch (_: SecurityException) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            HandoffExportResult.Failure(BackupError(BackupErrorCode.PERMISSION, "无法写入所选接力包位置"))
        } catch (error: Exception) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            HandoffExportResult.Failure(mapError(error))
        } finally {
            temporary.delete()
        }
    }

    override suspend fun inspect(
        sourceUri: String,
        onProgress: (BackupProgress) -> Unit,
    ): HandoffInspectResult = withContext(Dispatchers.IO) {
        val token = idFactory()
        var staged: StagedBackup? = null
        try {
            onProgress(BackupProgress(BackupPhase.PLANNING, 0, 4, "validating-handoff"))
            staged = archiveReader.inspectAndExtract(
                source = documentGateway.openForRead(sourceUri),
                stagingParent = File(privateRoot, "backup-staging"),
                operationId = token,
                compressedSizeHint = documentGateway.querySize(sourceUri),
                availableBytes = privateRoot.usableSpace,
            )
            planner.validateArchiveManifest(staged.manifest)
            currentCoroutineContext().ensureActive()
            val incoming = BackupCatalogCodec.decode(staged.files, formatVersion = 2)
            val current = catalogDataSource.snapshot()
            val currentAssets = assetInventory.collect(current, BackupOptions()).assets.sumOf { it.expectedSize }
            val currentCatalogBytes = BackupCatalogCodec.encode(current).values.sumOf { it.size.toLong() }
            val plan = planner.plan(
                stagedPlanToken = token,
                incoming = incoming,
                current = current,
                manifest = staged.manifest,
                stagedAssetPaths = staged.files.keys,
                stagingBytes = Math.addExact(staged.compressedBytes, staged.expandedBytes),
                currentSnapshotBytes = Math.addExact(currentAssets, currentCatalogBytes),
            )
            val preview = planner.preview(plan, incoming, current, staged.manifest)
            stagedPlans.retain(PreparedRestorePlan(plan, staged, incoming, current))
            staged = null
            onProgress(BackupProgress(BackupPhase.PLANNING, 4, 4, "handoff-preview-ready"))
            HandoffInspectResult.Success(preview)
        } catch (_: CancellationException) {
            staged?.cleanup()
            HandoffInspectResult.Cancelled
        } catch (_: SecurityException) {
            staged?.cleanup()
            HandoffInspectResult.Failure(BackupError(BackupErrorCode.PERMISSION, "无法读取所选接力包"))
        } catch (error: Exception) {
            staged?.cleanup()
            HandoffInspectResult.Failure(mapError(error))
        }
    }

    override suspend fun import(
        request: HandoffImportRequest,
        onProgress: (BackupProgress) -> Unit,
    ): HandoffImportResult = withContext(Dispatchers.IO) {
        val publisher = restorePublisher ?: return@withContext HandoffImportResult.Failure(
            BackupError(BackupErrorCode.UNKNOWN, "接力发布组件不可用"),
        )
        try {
            restoreRecovery?.reconcile()
            val prepared = stagedPlans.peek(request.stagedPlanToken)
                ?: stagedPlans.load(request.stagedPlanToken, File(privateRoot, "backup-staging"))
                ?: return@withContext HandoffImportResult.Failure(
                    BackupError(BackupErrorCode.CONFLICT, "接力预览已失效，请重新选择接力包"),
                )
            planner.validateArchiveManifest(prepared.staged.manifest)
            val expectedConflictIds = prepared.plan.conflicts.map { it.id }.toSet()
            require(request.resolutions.map { it.conflictId }.toSet() == expectedConflictIds) {
                "接力冲突选择不完整"
            }
            val importsNewBook = prepared.plan.entityActions.any {
                it.kind == RestoreEntityKind.BOOK &&
                    it.action in setOf(RestoreActionKind.INSERT, RestoreActionKind.COPY_AS_NEW)
            }
            onProgress(BackupProgress(BackupPhase.RESTORING, 0, 2, "publishing-handoff"))
            val result = publisher.publish(
                prepared,
                RestoreRequest(request.stagedPlanToken, RestoreMode.MERGE, request.resolutions),
            )
            if (result is RestoreResult.Success) {
                onProgress(BackupProgress(BackupPhase.VERIFYING, 2, 2, "handoff-complete"))
            }
            if (result !is RestoreResult.Failure || !prepared.staged.stagingRoot.exists()) {
                stagedPlans.consume(request.stagedPlanToken)
            }
            when (result) {
                is RestoreResult.Success -> HandoffImportResult.Success(
                    importedNewBook = importsNewBook,
                    appliedCount = prepared.plan.entityActions.count {
                        it.action in setOf(
                            RestoreActionKind.INSERT,
                            RestoreActionKind.REPLACE,
                            RestoreActionKind.COPY_AS_NEW,
                        )
                    },
                    skippedCount = result.skippedCount,
                    conflictCopyCount = result.conflictCopyCount,
                )
                RestoreResult.Cancelled -> HandoffImportResult.Cancelled
                is RestoreResult.Failure -> HandoffImportResult.Failure(result.error)
            }
        } catch (_: CancellationException) {
            HandoffImportResult.Cancelled
        } catch (error: Exception) {
            HandoffImportResult.Failure(mapError(error))
        }
    }

    override suspend fun discard(stagedPlanToken: String): Boolean = withContext(Dispatchers.IO) {
        restoreRecovery?.reconcile()
        val prepared = stagedPlans.consume(stagedPlanToken)
            ?: stagedPlans.load(stagedPlanToken, File(privateRoot, "backup-staging"))?.also {
                stagedPlans.consume(stagedPlanToken)
            }
            ?: return@withContext true
        prepared.staged.cleanup()
    }

    private fun BackupCatalogSnapshot.singleBookHandoff(
        bookId: String,
        includeBookText: Boolean,
    ): BackupCatalogSnapshot {
        val book = requireNotNull(books.singleOrNull { it.id == bookId }) { "所选书籍不存在" }
        val selectedMemberships = memberships.filter { it.bookId == bookId }
        val selectedGroupIds = selectedMemberships.map { it.groupId }.toSet()
        val source = bookSources.singleOrNull { it.bookId == bookId }
        if (includeBookText) requireNotNull(source) { "所选书籍正文资产缺失" }
        return BackupCatalogSnapshot(
            books = listOf(book.copy(groupId = null, customCoverAssetPath = null)),
            groups = groups.filter { it.id in selectedGroupIds },
            memberships = selectedMemberships,
            progress = progress.filter { it.bookId == bookId },
            annotations = annotations.filter { it.bookId == bookId },
            bookSettings = bookSettings
                .filter { it.bookId == bookId }
                .map { it.withTransferableFontReference() },
            bookSources = source?.let {
                listOf(it.copy(customCoverRelativePath = null))
            }.orEmpty(),
        )
    }

    private fun BackupBookSettingsRecord.withTransferableFontReference(): BackupBookSettingsRecord {
        val overrides = readerDataJson.decodeFromString<ReaderSettingsOverrides>(overridesJson)
        if (overrides.font !is ReaderFontRef.Imported) return this
        return copy(overridesJson = readerDataJson.encodeToString(overrides.copy(font = null)))
    }

    private fun mapError(error: Throwable): BackupError = when {
        error is BackupArchiveException -> BackupError(BackupErrorCode.SECURITY, "接力包无效或不安全")
        error is IllegalArgumentException || error is kotlinx.serialization.SerializationException ->
            BackupError(BackupErrorCode.INVALID_FORMAT, "接力包格式、身份或关系无效")
        error is BackupSourceChangedException ->
            BackupError(BackupErrorCode.SOURCE_CHANGED, "接力导出过程中本地数据发生变化，请重试")
        error is IOException && (
            error.message.orEmpty().contains("ENOSPC", ignoreCase = true) ||
                error.message.orEmpty().contains("no space", ignoreCase = true)
            ) -> BackupError(BackupErrorCode.SPACE, "接力包位置空间不足")
        error is IOException -> BackupError(BackupErrorCode.PROVIDER, "接力包读写失败，请重新选择位置")
        else -> BackupError(BackupErrorCode.UNKNOWN, "接力操作失败，请重试")
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
