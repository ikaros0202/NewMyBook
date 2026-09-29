package com.xinyue.reader.core.data

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RestoreJournalStore internal constructor(
    privateRoot: File,
    private val beforeReplace: () -> Unit = {},
) {
    private val root = File(privateRoot, JOURNAL_DIRECTORY).absoluteFile

    fun write(journal: RestoreJournal) {
        validate(journal)
        require(root.isDirectory || root.mkdirs()) { "无法创建恢复 journal 目录" }
        val target = journalFile(journal.operationId)
        val temporary = File(root, ".${journal.operationId}.json.tmp")
        try {
            val bytes = json.encodeToString(journal).encodeToByteArray()
            require(bytes.size <= MAX_JOURNAL_BYTES) { "恢复 journal 过大" }
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            beforeReplace()
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            runCatching {
                FileChannel.open(root.toPath(), StandardOpenOption.READ).use { it.force(true) }
            }
        } finally {
            temporary.delete()
        }
    }

    fun read(operationId: String): RestoreJournal? {
        val file = journalFile(operationId)
        if (!file.isFile) return null
        require(file.length() in 1..MAX_JOURNAL_BYTES.toLong()) { "恢复 journal 大小无效" }
        return json.decodeFromString<RestoreJournal>(file.readText(Charsets.UTF_8)).also(::validate)
    }

    fun list(): List<RestoreJournal> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
            .sortedBy { it.name }
            .map { file -> read(file.name.removeSuffix(".json")) ?: error("恢复 journal 消失") }
    }

    fun delete(operationId: String): Boolean {
        val file = journalFile(operationId)
        return !file.exists() || file.delete()
    }

    private fun journalFile(operationId: String): File {
        require(operationId.matches(ID_PATTERN)) { "恢复操作 ID 无效" }
        return File(root, "$operationId.json")
    }

    private fun validate(journal: RestoreJournal) {
        require(journal.operationId.matches(ID_PATTERN)) { "恢复操作 ID 无效" }
        val paths = listOf(journal.stagedRoot, journal.snapshotRoot) +
            journal.intendedTargets + journal.publishedMoves.map { it.targetRelativePath }
        paths.forEach { path -> require(BackupPathPolicy.normalize(path) == path) { "恢复 journal 路径无效" } }
        require(journal.intendedTargets.distinct().size == journal.intendedTargets.size) { "恢复目标重复" }
        require(journal.publishedMoves.map { it.targetRelativePath }.distinct().size == journal.publishedMoves.size) {
            "恢复发布记录重复"
        }
        require(journal.publishedMoves.all { it.targetRelativePath in journal.intendedTargets }) { "恢复发布记录不在计划内" }
        require(journal.expectedCatalogSha256.matches(SHA256_PATTERN)) { "恢复目录摘要无效" }
    }

    companion object {
        const val JOURNAL_DIRECTORY = "restore-journals"
        private const val MAX_JOURNAL_BYTES = 1024 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
