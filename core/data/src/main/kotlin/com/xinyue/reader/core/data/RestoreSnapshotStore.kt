package com.xinyue.reader.core.data

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class RestoreSnapshot(
    val root: File,
    val catalog: BackupCatalogSnapshot,
    val catalogSha256: String,
    val files: List<SnapshotFileRecord>,
)

@Serializable
data class SnapshotFileRecord(
    val targetRelativePath: String,
    val existed: Boolean,
    val sizeBytes: Long = 0,
    val sha256: String? = null,
)

class RestoreSnapshotStore(private val privateRoot: File) {
    private val snapshotsRoot = File(privateRoot, SNAPSHOT_DIRECTORY)

    fun create(
        operationId: String,
        catalog: BackupCatalogSnapshot,
        targetRelativePaths: List<String>,
        faultInjector: RestoreFaultInjector = RestoreFaultInjector.NONE,
    ): RestoreSnapshot {
        require(operationId.matches(ID_PATTERN)) { "恢复操作 ID 无效" }
        require(snapshotsRoot.isDirectory || snapshotsRoot.mkdirs()) { "无法创建恢复快照目录" }
        val targetRoot = File(snapshotsRoot, operationId)
        val temporary = File(snapshotsRoot, ".$operationId.tmp")
        require(!targetRoot.exists() && !temporary.exists()) { "恢复快照已存在" }
        try {
            require(temporary.mkdirs()) { "无法创建恢复快照临时目录" }
            val catalogRoot = File(temporary, "catalog").also { it.mkdirs() }
            BackupCatalogCodec.encode(catalog).forEach { (path, bytes) ->
                val target = File(catalogRoot, path.removePrefix("catalog/"))
                syncWrite(target, bytes)
            }
            faultInjector.hit(RestoreFaultPoint.AFTER_SNAPSHOT_CATALOG)
            val records = targetRelativePaths.distinct().sorted().map { relative ->
                require(BackupPathPolicy.normalize(relative) == relative) { "恢复目标路径无效" }
                val live = BackupPathPolicy.resolve(privateRoot, relative)
                val record = if (live.exists()) {
                    require(live.isFile && !java.nio.file.Files.isSymbolicLink(live.toPath())) { "恢复目标不是普通文件" }
                    val snapshotFile = BackupPathPolicy.resolve(File(temporary, "files"), relative)
                    snapshotFile.parentFile!!.mkdirs()
                    live.inputStream().buffered().use { input ->
                        FileOutputStream(snapshotFile).use { output -> input.copyTo(output); output.fd.sync() }
                    }
                    SnapshotFileRecord(relative, true, snapshotFile.length(), sha256(snapshotFile))
                } else {
                    SnapshotFileRecord(relative, false)
                }
                faultInjector.hit(RestoreFaultPoint.AFTER_EACH_SNAPSHOT_FILE)
                record
            }
            val descriptor = SnapshotDescriptor(
                catalogSha256 = BackupCatalogDigest.sha256(catalog),
                files = records,
                bookSources = catalog.bookSources,
                fontSources = catalog.fontSources,
            )
            syncWrite(File(temporary, DESCRIPTOR_FILE), json.encodeToString(descriptor).encodeToByteArray())
            require(temporary.renameTo(targetRoot)) { "无法原子发布恢复快照" }
            return load(targetRoot)
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
    }

    fun load(root: File): RestoreSnapshot {
        val normalizedRoot = root.absoluteFile.toPath().normalize()
        require(normalizedRoot.startsWith(snapshotsRoot.absoluteFile.toPath().normalize())) { "恢复快照路径越界" }
        val descriptorFile = File(root, DESCRIPTOR_FILE)
        require(descriptorFile.isFile && descriptorFile.length() in 1..MAX_DESCRIPTOR_BYTES) { "恢复快照描述无效" }
        val descriptor = json.decodeFromString<SnapshotDescriptor>(descriptorFile.readText(Charsets.UTF_8))
        val catalogFiles = BackupManifestCodec.CATALOG_PATHS.associateWith { path ->
            File(root, "catalog/${path.removePrefix("catalog/")}")
        }
        val catalog = BackupCatalogCodec.decode(catalogFiles).copy(
            bookSources = descriptor.bookSources,
            fontSources = descriptor.fontSources,
        )
        require(BackupCatalogDigest.sha256(catalog) == descriptor.catalogSha256) { "恢复快照目录摘要不匹配" }
        descriptor.files.forEach { record ->
            require(BackupPathPolicy.normalize(record.targetRelativePath) == record.targetRelativePath) { "恢复快照路径无效" }
            if (record.existed) {
                val file = BackupPathPolicy.resolve(File(root, "files"), record.targetRelativePath)
                require(file.isFile && file.length() == record.sizeBytes && sha256(file) == record.sha256) { "恢复快照文件校验失败" }
            }
        }
        return RestoreSnapshot(root, catalog, descriptor.catalogSha256, descriptor.files)
    }

    fun rollbackFiles(snapshot: RestoreSnapshot) {
        snapshot.files.forEach { record ->
            val live = BackupPathPolicy.resolve(privateRoot, record.targetRelativePath)
            if (!record.existed) {
                live.delete()
            } else {
                val saved = BackupPathPolicy.resolve(File(snapshot.root, "files"), record.targetRelativePath)
                atomicCopy(saved, live)
                require(live.length() == record.sizeBytes && sha256(live) == record.sha256) { "恢复快照回滚校验失败" }
            }
        }
    }

    fun cleanup(snapshot: RestoreSnapshot): Boolean = !snapshot.root.exists() || snapshot.root.deleteRecursively()

    private fun syncWrite(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output -> output.write(bytes); output.flush(); output.fd.sync() }
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

    private companion object {
        const val SNAPSHOT_DIRECTORY = "restore-snapshots"
        const val DESCRIPTOR_FILE = "snapshot.json"
        const val MAX_DESCRIPTOR_BYTES = 4L * 1024 * 1024
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class SnapshotDescriptor(
    val catalogSha256: String,
    val files: List<SnapshotFileRecord>,
    val bookSources: List<BackupBookSource> = emptyList(),
    val fontSources: List<BackupFontSource> = emptyList(),
)
