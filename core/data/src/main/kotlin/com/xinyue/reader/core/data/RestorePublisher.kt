package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupError
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.RestoreAssetAction
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

enum class RestoreFaultPoint {
    AFTER_PLAN_CONFIRMED,
    AFTER_SNAPSHOT_CATALOG,
    AFTER_EACH_SNAPSHOT_FILE,
    AFTER_JOURNAL_PREPARED,
    AFTER_FIRST_FILE_PUBLISHED,
    AFTER_ALL_FILES_PUBLISHED,
    BEFORE_DATABASE_TRANSACTION,
    DURING_DATABASE_TRANSACTION,
    AFTER_DATABASE_COMMIT,
    DURING_VERIFICATION,
    DURING_CLEANUP,
}

fun interface RestoreFaultInjector {
    fun hit(point: RestoreFaultPoint)

    companion object { val NONE = RestoreFaultInjector { } }
}

data class ResolvedRestore(
    val desiredCatalog: BackupCatalogSnapshot,
    val assetActions: List<RestoreAssetAction>,
    val restoredCount: Int,
    val skippedCount: Int,
    val metadataOnlyBookCount: Int,
    val conflictCopyCount: Int = 0,
    val deleteTargetRelativePaths: List<String> = emptyList(),
)

fun interface RestorePlanResolver {
    fun resolve(prepared: PreparedRestorePlan, request: RestoreRequest): ResolvedRestore
}

interface RestoreDatabaseGateway {
    suspend fun snapshot(): BackupCatalogSnapshot
    suspend fun replaceAll(snapshot: BackupCatalogSnapshot)
}

class RestorePublisher(
    private val privateRoot: File,
    private val database: RestoreDatabaseGateway,
    private val resolver: RestorePlanResolver,
    private val journalStore: RestoreJournalStore,
    private val snapshotStore: RestoreSnapshotStore,
    private val faultInjector: RestoreFaultInjector = RestoreFaultInjector.NONE,
    private val freeBytes: () -> Long = { privateRoot.usableSpace },
) {
    suspend fun publish(prepared: PreparedRestorePlan, request: RestoreRequest): RestoreResult {
        var snapshot: RestoreSnapshot? = null
        var journal: RestoreJournal? = null
        var resolved: ResolvedRestore? = null
        try {
            require(request.stagedPlanToken == prepared.plan.stagedPlanToken) { "恢复计划 token 不匹配" }
            require(freeBytes() >= prepared.plan.preview.requiredFreeBytes) { "恢复所需空间不足" }
            val live = database.snapshot()
            require(BackupCatalogDigest.sha256(live) == BackupCatalogDigest.sha256(prepared.currentCatalog)) {
                "现有数据已变化，请重新预览恢复计划"
            }
            resolved = resolver.resolve(prepared, request)
            faultInjector.hit(RestoreFaultPoint.AFTER_PLAN_CONFIRMED)
            val publishedTargets = resolved.assetActions.map { it.targetRelativePath }
            require(publishedTargets.toSet().size == publishedTargets.size) { "恢复发布目标重复" }
            require(resolved.deleteTargetRelativePaths.none { it in publishedTargets }) { "恢复发布与删除目标冲突" }
            val targets = (publishedTargets + resolved.deleteTargetRelativePaths).distinct().sorted()
            snapshot = snapshotStore.create(prepared.plan.stagedPlanToken, live, targets, faultInjector)
            val expectedCatalog = BackupCatalogDigest.sha256(resolved.desiredCatalog)
            journal = RestoreJournal(
                operationId = prepared.plan.stagedPlanToken,
                phase = RestorePhase.PREPARED,
                stagedRoot = prepared.staged.stagingRoot.relativeTo(privateRoot).invariantSeparatorsPath,
                snapshotRoot = snapshot.root.relativeTo(privateRoot).invariantSeparatorsPath,
                intendedTargets = targets.sorted(),
                publishedMoves = emptyList(),
                expectedCatalogSha256 = expectedCatalog,
                request = request,
            )
            journalStore.write(journal)
            var activeJournal = requireNotNull(journal)
            faultInjector.hit(RestoreFaultPoint.AFTER_JOURNAL_PREPARED)
            resolved.assetActions.sortedBy { it.targetRelativePath }.forEachIndexed { index, action ->
                val source = requireNotNull(prepared.staged.files[action.sourcePath]) { "恢复暂存资产缺失" }
                require(source.length() == action.sizeBytes && sha256(source) == action.sha256) { "恢复暂存资产校验失败" }
                val target = BackupPathPolicy.resolve(privateRoot, action.targetRelativePath)
                val replaced = target.exists()
                atomicCopy(source, target)
                require(target.length() == action.sizeBytes && sha256(target) == action.sha256) { "恢复发布资产校验失败" }
                activeJournal = activeJournal.copy(
                    publishedMoves = activeJournal.publishedMoves + PublishedMove(action.targetRelativePath, replaced),
                )
                journal = activeJournal
                journalStore.write(activeJournal)
                if (index == 0) faultInjector.hit(RestoreFaultPoint.AFTER_FIRST_FILE_PUBLISHED)
            }
            resolved.deleteTargetRelativePaths.sorted().forEach { relative ->
                val target = BackupPathPolicy.resolve(privateRoot, relative)
                val replaced = target.exists()
                require(!replaced || target.isFile) { "恢复删除目标不是普通文件" }
                if (replaced) require(target.delete()) { "无法删除恢复覆盖资产" }
                activeJournal = activeJournal.copy(
                    publishedMoves = activeJournal.publishedMoves + PublishedMove(relative, replaced),
                )
                journal = activeJournal
                journalStore.write(activeJournal)
            }
            activeJournal = activeJournal.copy(phase = RestorePhase.FILES_PUBLISHED)
            journal = activeJournal
            journalStore.write(activeJournal)
            faultInjector.hit(RestoreFaultPoint.AFTER_ALL_FILES_PUBLISHED)
            faultInjector.hit(RestoreFaultPoint.BEFORE_DATABASE_TRANSACTION)
            database.replaceAll(resolved.desiredCatalog)
            faultInjector.hit(RestoreFaultPoint.AFTER_DATABASE_COMMIT)
            activeJournal = activeJournal.copy(phase = RestorePhase.DATABASE_COMMITTED)
            journal = activeJournal
            journalStore.write(activeJournal)
            faultInjector.hit(RestoreFaultPoint.DURING_VERIFICATION)
            verify(resolved)
            activeJournal = activeJournal.copy(phase = RestorePhase.VERIFIED)
            journal = activeJournal
            journalStore.write(activeJournal)
            activeJournal = activeJournal.copy(phase = RestorePhase.CLEANUP_PENDING)
            journal = activeJournal
            journalStore.write(activeJournal)
            faultInjector.hit(RestoreFaultPoint.DURING_CLEANUP)
            cleanup(snapshot, prepared, activeJournal)
            return resolved.success()
        } catch (_: CancellationException) {
            if (journal?.phase?.ordinal ?: -1 >= RestorePhase.VERIFIED.ordinal) return requireNotNull(resolved).success()
            rollback(snapshot, prepared, journal)
            return RestoreResult.Cancelled
        } catch (error: Exception) {
            if (journal?.phase?.ordinal ?: -1 >= RestorePhase.VERIFIED.ordinal) return requireNotNull(resolved).success()
            rollback(snapshot, prepared, journal)
            val code = if (error.message?.contains("空间") == true) BackupErrorCode.SPACE else BackupErrorCode.UNKNOWN
            return RestoreResult.Failure(BackupError(code, "恢复失败，现有数据已保持或回滚"))
        }
    }

    internal suspend fun verify(resolved: ResolvedRestore) {
        require(BackupCatalogDigest.sha256(database.snapshot()) == BackupCatalogDigest.sha256(resolved.desiredCatalog)) {
            "恢复目录等价性校验失败"
        }
        resolved.assetActions.forEach { action ->
            val target = BackupPathPolicy.resolve(privateRoot, action.targetRelativePath)
            require(target.isFile && target.length() == action.sizeBytes && sha256(target) == action.sha256) { "恢复文件等价性校验失败" }
        }
        resolved.deleteTargetRelativePaths.forEach { relative ->
            require(!BackupPathPolicy.resolve(privateRoot, relative).exists()) { "恢复删除资产仍然存在" }
        }
    }

    private suspend fun rollback(snapshot: RestoreSnapshot?, prepared: PreparedRestorePlan, journal: RestoreJournal?) {
        if (snapshot != null) {
            runCatching { database.replaceAll(snapshot.catalog) }
            runCatching { snapshotStore.rollbackFiles(snapshot) }
            runCatching { snapshotStore.cleanup(snapshot) }
        }
        runCatching { prepared.staged.cleanup() }
        journal?.let { runCatching { journalStore.delete(it.operationId) } }
    }

    private fun cleanup(snapshot: RestoreSnapshot, prepared: PreparedRestorePlan, journal: RestoreJournal) {
        require(snapshotStore.cleanup(snapshot)) { "恢复快照清理失败" }
        require(prepared.staged.cleanup()) { "恢复暂存清理失败" }
        journalStore.write(journal.copy(phase = RestorePhase.COMPLETED))
        require(journalStore.delete(journal.operationId)) { "恢复 journal 清理失败" }
    }

    private fun atomicCopy(source: File, target: File) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.restore.tmp")
        try {
            source.inputStream().buffered().use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() }
            }
            java.nio.file.Files.move(
                temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally { temporary.delete() }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ResolvedRestore.success() = RestoreResult.Success(restoredCount, skippedCount, conflictCopyCount, metadataOnlyBookCount)
}

object BackupCatalogDigest {
    fun sha256(catalog: BackupCatalogSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BackupCatalogCodec.encode(catalog).forEach { (path, bytes) ->
            digest.update(path.encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
