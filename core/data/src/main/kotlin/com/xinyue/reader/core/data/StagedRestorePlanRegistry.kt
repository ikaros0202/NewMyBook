package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.RestorePlan
import com.xinyue.reader.core.domain.model.BackupManifest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class PreparedRestorePlan(
    val plan: RestorePlan,
    val staged: StagedBackup,
    val backupCatalog: BackupCatalogSnapshot,
    val currentCatalog: BackupCatalogSnapshot,
)

@Singleton
class StagedRestorePlanRegistry @Inject constructor() {
    private val plans = ConcurrentHashMap<String, PreparedRestorePlan>()

    fun retain(prepared: PreparedRestorePlan) {
        val token = prepared.plan.stagedPlanToken
        require(token.matches(TOKEN_PATTERN)) { "恢复计划 token 无效" }
        require(plans[token] == null) { "恢复计划 token 重复" }
        val record = StoredPreparedRestorePlan(
            prepared.plan,
            prepared.staged.manifest,
            prepared.backupCatalog,
            prepared.currentCatalog,
            prepared.staged.compressedBytes,
            prepared.staged.expandedBytes,
        )
        val bytes = json.encodeToString(record).encodeToByteArray()
        require(bytes.size.toLong() <= MAX_PLAN_BYTES) { "恢复计划过大" }
        val target = File(prepared.staged.stagingRoot, PLAN_FILE_NAME)
        val temporary = File(prepared.staged.stagingRoot, "$PLAN_FILE_NAME.tmp")
        try {
            temporary.outputStream().buffered().use { it.write(bytes) }
            require(temporary.renameTo(target)) { "无法原子保存恢复计划" }
            require(plans.putIfAbsent(token, prepared) == null) { "恢复计划 token 重复" }
        } finally {
            temporary.delete()
        }
    }

    fun peek(token: String): PreparedRestorePlan? = plans[token]

    fun load(token: String, stagingParent: File): PreparedRestorePlan? {
        plans[token]?.let { return it }
        if (!token.matches(TOKEN_PATTERN)) return null
        val parent = stagingParent.absoluteFile.toPath().normalize()
        val stagingRoot = File(stagingParent, "import-$token").absoluteFile
        if (!stagingRoot.toPath().normalize().startsWith(parent)) return null
        val descriptor = File(stagingRoot, PLAN_FILE_NAME)
        if (!descriptor.isFile || descriptor.length() !in 1..MAX_PLAN_BYTES) return null
        return runCatching {
            val stored = json.decodeFromString<StoredPreparedRestorePlan>(descriptor.readText(Charsets.UTF_8))
            require(stored.plan.stagedPlanToken == token) { "恢复计划 token 不匹配" }
            val archive = File(stagingRoot, "archive.xinyuebackup")
            val extractionRoot = File(stagingRoot, "extracted")
            require(archive.isFile && extractionRoot.isDirectory) { "恢复暂存文件缺失" }
            val files = stored.manifest.entries.associate { entry ->
                val file = BackupPathPolicy.resolve(extractionRoot, entry.path)
                require(file.isFile && file.length() == entry.uncompressedSize) { "恢复暂存条目缺失" }
                entry.path to file
            }
            PreparedRestorePlan(
                stored.plan,
                StagedBackup(
                    stagingRoot, archive, extractionRoot, stored.manifest, files,
                    stored.compressedBytes, stored.expandedBytes,
                ),
                stored.backupCatalog,
                stored.currentCatalog,
            )
        }.getOrNull()?.also { plans.putIfAbsent(token, it) }
    }

    fun consume(token: String): PreparedRestorePlan? = plans.remove(token)

    internal fun takeForTest(token: String): PreparedRestorePlan = requireNotNull(plans[token])
    internal fun loadForTest(token: String, stagingParent: File): PreparedRestorePlan = requireNotNull(load(token, stagingParent))
    internal fun sizeForTest(): Int = plans.size

    private companion object {
        const val PLAN_FILE_NAME = "restore-plan.json"
        const val MAX_PLAN_BYTES = 64L * 1024 * 1024
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredPreparedRestorePlan(
    val plan: RestorePlan,
    val manifest: BackupManifest,
    val backupCatalog: BackupCatalogSnapshot,
    val currentCatalog: BackupCatalogSnapshot,
    val compressedBytes: Long,
    val expandedBytes: Long,
)
