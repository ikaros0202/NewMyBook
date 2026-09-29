package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.domain.model.BackupError
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import com.xinyue.reader.core.domain.repository.BackupService
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

@Singleton
class LocalBackupService internal constructor(
    private val catalogDataSource: BackupCatalogDataSource,
    private val assetInventory: BackupAssetInventory,
    private val archiveWriter: BackupArchiveWriter,
    private val documentGateway: BackupDocumentGateway,
    private val privateRoot: File,
    private val appVersion: String,
    private val nowEpochMillis: () -> Long,
    private val idFactory: () -> String,
    private val archiveReader: SecureBackupArchive = SecureBackupArchive(),
    private val restorePlanner: RestorePlanner = RestorePlanner { UUID.randomUUID().toString() },
    private val stagedPlans: StagedRestorePlanRegistry = StagedRestorePlanRegistry(),
    private val restorePublisher: RestorePublisher? = null,
    private val restoreRecovery: RestoreRecovery? = null,
) : BackupService {
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
        archiveReader = SecureBackupArchive(),
        restorePlanner = RestorePlanner { UUID.randomUUID().toString() },
        stagedPlans = stagedPlans,
        restorePublisher = restorePublisher,
        restoreRecovery = restoreRecovery,
    )

    override suspend fun export(
        destinationUri: String,
        options: BackupOptions,
        onProgress: (BackupProgress) -> Unit,
    ): BackupExportResult = withContext(Dispatchers.IO) {
        val operationId = idFactory()
        val stagingRoot = File(privateRoot, "backup-staging").apply { mkdirs() }
        val temporary = File(stagingRoot, "export-$operationId.tmp")
        var destinationAttempted = false
        var lastCompleted = 0L
        fun progress(value: BackupProgress) {
            lastCompleted = maxOf(lastCompleted, value.completed)
            onProgress(value.copy(completed = lastCompleted, total = maxOf(value.total, lastCompleted)))
        }
        try {
            check(temporary.createNewFile()) { "无法创建备份临时文件" }
            progress(BackupProgress(BackupPhase.PREPARING, 0, 1, "preparing-snapshot"))
            val snapshot = catalogDataSource.snapshot()
            val inventory = assetInventory.collect(snapshot, options)
            val written = archiveWriter.write(
                temporary, inventory.catalog, inventory.assets, options, appVersion, nowEpochMillis(), ::progress,
            )
            currentCoroutineContext().ensureActive()
            destinationAttempted = true
            documentGateway.openForWrite(destinationUri).use { output ->
                val copyBase = lastCompleted
                val copyTotal = copyBase + temporary.length()
                var copied = 0L
                temporary.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        progress(BackupProgress(BackupPhase.WRITING, copyBase + copied, copyTotal, "copying-destination"))
                    }
                }
                output.flush()
                check(copied == temporary.length()) { "备份目标写入不完整" }
            }
            BackupExportResult.Success(operationId, written.archiveSha256, written.bytesWritten)
        } catch (_: CancellationException) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            BackupExportResult.Cancelled
        } catch (_: SecurityException) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            BackupExportResult.Failure(BackupError(BackupErrorCode.PERMISSION, "无法写入所选备份位置"))
        } catch (error: Exception) {
            if (destinationAttempted) documentGateway.invalidate(destinationUri)
            BackupExportResult.Failure(mapError(error))
        } finally {
            temporary.delete()
        }
    }

    override suspend fun inspect(
        sourceUri: String,
        onProgress: (BackupProgress) -> Unit,
    ): BackupInspectResult = withContext(Dispatchers.IO) {
        val token = idFactory()
        val stagingParent = File(privateRoot, "backup-staging")
        var staged: StagedBackup? = null
        try {
            onProgress(BackupProgress(BackupPhase.PLANNING, 0, 4, "copying-and-validating"))
            staged = archiveReader.inspectAndExtract(
                source = documentGateway.openForRead(sourceUri),
                stagingParent = stagingParent,
                operationId = token,
                compressedSizeHint = documentGateway.querySize(sourceUri),
                availableBytes = privateRoot.usableSpace,
            )
            currentCoroutineContext().ensureActive()
            onProgress(BackupProgress(BackupPhase.PLANNING, 1, 4, "decoding-catalogs"))
            val backupCatalog = BackupCatalogCodec.decode(staged.files, staged.manifest.formatVersion)
            onProgress(BackupProgress(BackupPhase.SNAPSHOTTING, 2, 4, "reading-current-snapshot"))
            val currentCatalog = catalogDataSource.snapshot()
            val currentAssets = assetInventory.collect(currentCatalog, BackupOptions()).assets.sumOf { it.expectedSize }
            val currentCatalogBytes = BackupCatalogCodec.encode(currentCatalog).values.sumOf { it.size.toLong() }
            val plan = restorePlanner.plan(
                stagedPlanToken = token,
                backup = backupCatalog,
                current = currentCatalog,
                manifest = staged.manifest,
                stagedAssetPaths = staged.files.keys,
                stagingBytes = Math.addExact(staged.compressedBytes, staged.expandedBytes),
                currentSnapshotBytes = Math.addExact(currentAssets, currentCatalogBytes),
            )
            onProgress(BackupProgress(BackupPhase.PLANNING, 4, 4, "restore-preview-ready"))
            stagedPlans.retain(PreparedRestorePlan(plan, staged, backupCatalog, currentCatalog))
            staged = null
            BackupInspectResult.Success(plan.preview)
        } catch (_: CancellationException) {
            staged?.cleanup()
            BackupInspectResult.Cancelled
        } catch (_: SecurityException) {
            staged?.cleanup()
            BackupInspectResult.Failure(BackupError(BackupErrorCode.PERMISSION, "无法读取所选备份文件"))
        } catch (error: Exception) {
            staged?.cleanup()
            BackupInspectResult.Failure(mapError(error))
        }
    }

    override suspend fun restore(
        request: RestoreRequest,
        onProgress: (BackupProgress) -> Unit,
    ): RestoreResult = withContext(Dispatchers.IO) {
        val publisher = restorePublisher
            ?: return@withContext RestoreResult.Failure(BackupError(BackupErrorCode.UNKNOWN, "恢复组件不可用"))
        try {
            restoreRecovery?.reconcile()
            val prepared = stagedPlans.peek(request.stagedPlanToken)
                ?: stagedPlans.load(request.stagedPlanToken, File(privateRoot, "backup-staging"))
                ?: return@withContext RestoreResult.Failure(
                    BackupError(BackupErrorCode.CONFLICT, "恢复预览已失效，请重新选择备份文件"),
                )
            onProgress(BackupProgress(BackupPhase.RESTORING, 0, 2, "publishing-restore"))
            val result = publisher.publish(prepared, request)
            if (result is RestoreResult.Success) {
                onProgress(BackupProgress(BackupPhase.VERIFYING, 2, 2, "restore-complete"))
            }
            if (result !is RestoreResult.Failure || !prepared.staged.stagingRoot.exists()) {
                stagedPlans.consume(request.stagedPlanToken)
            }
            result
        } catch (_: CancellationException) {
            RestoreResult.Cancelled
        } catch (error: Exception) {
            RestoreResult.Failure(mapError(error))
        }
    }

    override suspend fun discardRestorePlan(stagedPlanToken: String): Boolean = withContext(Dispatchers.IO) {
        restoreRecovery?.reconcile()
        val prepared = stagedPlans.consume(stagedPlanToken)
            ?: stagedPlans.load(stagedPlanToken, File(privateRoot, "backup-staging"))?.also {
                stagedPlans.consume(stagedPlanToken)
            }
            ?: return@withContext true
        prepared.staged.cleanup()
    }

    private fun mapError(error: Throwable): BackupError {
        if (error is BackupArchiveException) {
            return BackupError(BackupErrorCode.SECURITY, "备份文件无效或不安全")
        }
        if (error is IllegalArgumentException || error is kotlinx.serialization.SerializationException) {
            return BackupError(BackupErrorCode.INVALID_FORMAT, "备份数据格式或关系无效")
        }
        if (error is BackupSourceChangedException || error.message?.contains("备份过程中发生变化") == true) {
            return BackupError(BackupErrorCode.SOURCE_CHANGED, "数据在备份过程中发生变化，请重试")
        }
        if (error is IOException) {
            val noSpace = error.message.orEmpty().contains("ENOSPC", ignoreCase = true) ||
                error.message.orEmpty().contains("no space", ignoreCase = true)
            return if (noSpace) BackupError(BackupErrorCode.SPACE, "备份位置空间不足")
            else BackupError(BackupErrorCode.PROVIDER, "备份写入失败，请重新选择位置")
        }
        return BackupError(BackupErrorCode.UNKNOWN, "备份失败，请重试")
    }
}
