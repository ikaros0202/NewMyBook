package com.xinyue.reader.core.data

import java.io.File
import java.security.MessageDigest

class RestoreRecovery(
    private val privateRoot: File,
    private val database: RestoreDatabaseGateway,
    private val resolver: RestorePlanResolver,
    private val journalStore: RestoreJournalStore,
    private val snapshotStore: RestoreSnapshotStore,
    private val stagedPlans: StagedRestorePlanRegistry,
) {
    suspend fun reconcile() {
        journalStore.list().forEach { journal -> reconcile(journal) }
    }

    private suspend fun reconcile(journal: RestoreJournal) {
        when (journal.phase) {
            RestorePhase.PREPARED,
            RestorePhase.FILES_PUBLISHED,
            -> rollback(journal)

            RestorePhase.DATABASE_COMMITTED -> reconcileCommitted(journal)
            RestorePhase.VERIFIED,
            RestorePhase.CLEANUP_PENDING,
            -> cleanupNewState(journal)

            RestorePhase.COMPLETED -> require(journalStore.delete(journal.operationId)) { "无法删除已完成恢复 journal" }
        }
    }

    private suspend fun reconcileCommitted(journal: RestoreJournal) {
        val request = journal.request
        val prepared = stagedPlans.load(journal.operationId, File(privateRoot, "backup-staging"))
        if (request == null || prepared == null) {
            rollback(journal)
            return
        }
        val resolved = runCatching { resolver.resolve(prepared, request) }.getOrNull()
        if (resolved != null && BackupCatalogDigest.sha256(resolved.desiredCatalog) == journal.expectedCatalogSha256 && verify(resolved)) {
            journalStore.write(journal.copy(phase = RestorePhase.VERIFIED))
            journalStore.write(journal.copy(phase = RestorePhase.CLEANUP_PENDING))
            cleanupNewState(journal.copy(phase = RestorePhase.CLEANUP_PENDING))
        } else {
            rollback(journal)
        }
    }

    private suspend fun rollback(journal: RestoreJournal) {
        val snapshotRoot = BackupPathPolicy.resolve(privateRoot, journal.snapshotRoot)
        val snapshot = snapshotStore.load(snapshotRoot)
        database.replaceAll(snapshot.catalog)
        snapshotStore.rollbackFiles(snapshot)
        require(snapshotStore.cleanup(snapshot)) { "恢复回滚快照清理失败" }
        val stagedRoot = BackupPathPolicy.resolve(privateRoot, journal.stagedRoot)
        require(!stagedRoot.exists() || stagedRoot.deleteRecursively()) { "恢复回滚暂存清理失败" }
        require(journalStore.delete(journal.operationId)) { "恢复回滚 journal 清理失败" }
    }

    private fun cleanupNewState(journal: RestoreJournal) {
        val snapshotRoot = BackupPathPolicy.resolve(privateRoot, journal.snapshotRoot)
        require(!snapshotRoot.exists() || snapshotRoot.deleteRecursively()) { "恢复快照清理失败" }
        val stagedRoot = BackupPathPolicy.resolve(privateRoot, journal.stagedRoot)
        require(!stagedRoot.exists() || stagedRoot.deleteRecursively()) { "恢复暂存清理失败" }
        journalStore.write(journal.copy(phase = RestorePhase.COMPLETED))
        require(journalStore.delete(journal.operationId)) { "恢复 journal 清理失败" }
    }

    private suspend fun verify(resolved: ResolvedRestore): Boolean {
        if (BackupCatalogDigest.sha256(database.snapshot()) != BackupCatalogDigest.sha256(resolved.desiredCatalog)) return false
        return resolved.assetActions.all { action ->
            val target = runCatching { BackupPathPolicy.resolve(privateRoot, action.targetRelativePath) }.getOrNull()
                ?: return@all false
            target.isFile && target.length() == action.sizeBytes && sha256(target) == action.sha256
        } && resolved.deleteTargetRelativePaths.all { relative ->
            runCatching { BackupPathPolicy.resolve(privateRoot, relative) }.getOrNull()?.exists() == false
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
