package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class HandoffImportRequestStore internal constructor(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "backup-staging"))

    fun save(request: HandoffImportRequest) {
        require(request.stagedPlanToken.matches(BackupWork.ID_PATTERN)) { "接力预览 token 无效" }
        require(request.resolutions.map { it.conflictId }.distinct().size == request.resolutions.size) {
            "接力冲突选择重复"
        }
        val directory = planRoot(request.stagedPlanToken)
        require(directory.isDirectory) { "接力预览已失效" }
        val bytes = json.encodeToString(request).encodeToByteArray()
        require(bytes.size in 1..MAX_BYTES) { "接力确认数据过大" }
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, ".$FILE_NAME.tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.flush(); it.fd.sync() }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun load(token: String): HandoffImportRequest? {
        if (!token.matches(BackupWork.ID_PATTERN)) return null
        val file = File(planRoot(token), FILE_NAME)
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return null
        return runCatching { json.decodeFromString<HandoffImportRequest>(file.readText(Charsets.UTF_8)) }
            .getOrNull()
            ?.takeIf { it.stagedPlanToken == token }
    }

    fun delete(token: String): Boolean {
        if (!token.matches(BackupWork.ID_PATTERN)) return false
        val file = File(planRoot(token), FILE_NAME)
        return !file.exists() || file.delete()
    }

    private fun planRoot(token: String): File {
        val parent = root.absoluteFile.toPath().normalize()
        val candidate = File(root, "import-$token").absoluteFile
        require(candidate.toPath().normalize().startsWith(parent)) { "接力确认路径越界" }
        return candidate
    }

    private companion object {
        const val FILE_NAME = "confirmed-handoff-request.json"
        const val MAX_BYTES = 4 * 1024 * 1024
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    }
}
