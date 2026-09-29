package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupManifest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile

data class StagedBackup(
    val stagingRoot: File,
    val archiveFile: File,
    val extractionRoot: File,
    val manifest: BackupManifest,
    val files: Map<String, File>,
    val compressedBytes: Long,
    val expandedBytes: Long,
) {
    fun cleanup(): Boolean = !stagingRoot.exists() || stagingRoot.deleteRecursively()
}

class SecureBackupArchive internal constructor(
    private val maxCompressedBytes: Long = BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES,
) {
    fun inspectAndExtract(
        source: InputStream,
        stagingParent: File,
        operationId: String,
        compressedSizeHint: Long? = null,
        availableBytes: Long = stagingParent.usableSpace,
    ): StagedBackup {
        require(operationId.matches(OPERATION_ID)) { "备份操作标识无效" }
        require(maxCompressedBytes >= 0) { "备份压缩大小限制无效" }
        val operationRoot = File(stagingParent, "import-$operationId")
        try {
            require(stagingParent.isDirectory || stagingParent.mkdirs()) { "无法创建备份暂存根目录" }
            require(compressedSizeHint == null || compressedSizeHint in 0..maxCompressedBytes) { "备份文件过大" }
            require(compressedSizeHint == null || compressedSizeHint <= availableBytes) { "暂存空间不足" }
            require(!operationRoot.exists() && operationRoot.mkdirs()) { "无法创建备份暂存目录" }
            val archiveFile = File(operationRoot, "archive.xinyuebackup")
            val compressedBytes = copyBounded(source, archiveFile)
            require(compressedBytes <= availableBytes) { "暂存空间不足" }
            return inspectAndExtractFile(operationRoot, archiveFile, compressedBytes, availableBytes)
        } catch (error: Exception) {
            operationRoot.deleteRecursively()
            if (error is BackupArchiveException) throw error
            throw BackupArchiveException("备份文件无效或不安全", error)
        }
    }

    private fun copyBounded(source: InputStream, archiveFile: File): Long {
        val counted = CountingLimitedInputStream(source, maxCompressedBytes)
        counted.use { input ->
            archiveFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        return counted.count
    }

    private fun inspectAndExtractFile(
        operationRoot: File,
        archiveFile: File,
        compressedBytes: Long,
        availableBytes: Long,
    ): StagedBackup = ZipFile.builder().setFile(archiveFile).setUseUnicodeExtraFields(true).setMaxNumberOfDisks(1).get().use { zip ->
        val allEntries = zip.entries.asSequence().toList()
        require(allEntries.size <= BackupSafetyLimits.MAX_ENTRY_COUNT + 1) { "备份条目数量超出限制" }
        val normalized = BackupPathPolicy.requireUnique(allEntries.map { it.name })
        require(normalized == allEntries.map { it.name }) { "备份条目名称必须使用 NFC" }
        allEntries.forEach { validateCentralEntry(zip, it) }

        val manifestEntries = allEntries.filter { it.name == BackupManifestCodec.MANIFEST_PATH }
        require(manifestEntries.size == 1) { "备份清单数量无效" }
        val manifestBytes = readAndVerifyCentralEntry(zip, manifestEntries.single(), BackupSafetyLimits.MAX_CATALOG_BYTES)
        val manifest = BackupManifestCodec.decode(manifestBytes)
        val payloadEntries = allEntries.filterNot { it.name == BackupManifestCodec.MANIFEST_PATH }
        val byPath = payloadEntries.associateBy { it.name }
        require(byPath.keys == manifest.entries.map { it.path }.toSet()) { "备份清单与归档条目不一致" }

        var expandedBytes = 0L
        manifest.entries.forEach { expected ->
            val actual = byPath.getValue(expected.path)
            require(actual.size == expected.uncompressedSize) { "备份条目大小与清单不一致" }
            BackupSafetyLimits.requireEntrySize(expected.kind, actual.size)
            BackupSafetyLimits.requireCompressionRatio(actual.compressedSize, actual.size)
            require(actual.size <= BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES - expandedBytes) { "备份展开总大小超出限制" }
            expandedBytes += actual.size
        }
        BackupSafetyLimits.requireTotalExpandedBytes(expandedBytes)
        require(compressedBytes <= availableBytes && expandedBytes <= availableBytes - compressedBytes) { "暂存空间不足" }

        val extractionRoot = File(operationRoot, "extracted")
        require(extractionRoot.mkdir()) { "无法创建解压目录" }
        val files = LinkedHashMap<String, File>()
        var observedTotal = 0L
        manifest.entries.forEach { expected ->
            val entry = byPath.getValue(expected.path)
            val target = BackupPathPolicy.resolve(extractionRoot, expected.path)
            val parent = requireNotNull(target.parentFile) { "解压目标目录无效" }
            require(parent.isDirectory || parent.mkdirs()) { "无法创建解压目录" }
            val digest = MessageDigest.getInstance("SHA-256")
            val crc = CRC32()
            var observed = 0L
            CountingLimitedInputStream(zip.getInputStream(entry), expected.uncompressedSize).use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        observed += count
                        observedTotal += count
                        require(observedTotal <= BackupSafetyLimits.MAX_TOTAL_EXPANDED_BYTES) { "备份展开总大小超出限制" }
                        digest.update(buffer, 0, count)
                        crc.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(observed == expected.uncompressedSize && observed == entry.size) { "备份条目实际大小不一致" }
            require(crc.value == entry.crc) { "备份条目 CRC 校验失败" }
            require(digest.digest().hex() == expected.sha256) { "备份条目 SHA-256 校验失败" }
            files[expected.path] = target
        }
        StagedBackup(operationRoot, archiveFile, extractionRoot, manifest, files, compressedBytes, expandedBytes)
    }

    private fun validateCentralEntry(zip: ZipFile, entry: ZipArchiveEntry) {
        require(!entry.isDirectory && !entry.isUnixSymlink) { "备份只允许普通文件" }
        val type = entry.unixMode and UnixStat.FILE_TYPE_FLAG
        require(type == 0 || type == UnixStat.FILE_FLAG) { "备份只允许普通文件" }
        require(!entry.generalPurposeBit.usesEncryption() && !entry.generalPurposeBit.usesStrongEncryption()) {
            "不支持加密备份条目"
        }
        require(entry.rawFlag and SUPPORTED_GENERAL_PURPOSE_FLAGS.inv() == 0) { "备份条目包含不支持的标志" }
        require(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED) { "备份压缩方法不受支持" }
        require(entry.diskNumberStart == 0L) { "不支持分卷备份" }
        require(entry.size >= 0 && entry.compressedSize >= 0 && entry.crc >= 0) { "备份条目大小未知" }
        require(zip.canReadEntryData(entry)) { "备份条目功能不受支持" }
        BackupPathPolicy.normalize(entry.name)
    }

    private fun readAndVerifyCentralEntry(zip: ZipFile, entry: ZipArchiveEntry, limit: Long): ByteArray {
        require(entry.size in 0..limit) { "备份清单过大" }
        BackupSafetyLimits.requireCompressionRatio(entry.compressedSize, entry.size)
        val crc = CRC32()
        val output = java.io.ByteArrayOutputStream(entry.size.toInt())
        val counted = CountingLimitedInputStream(zip.getInputStream(entry), limit)
        counted.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                crc.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
        require(counted.count == entry.size && crc.value == entry.crc) { "备份清单校验失败" }
        return output.toByteArray()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val OPERATION_ID = Regex("[A-Za-z0-9_-]{1,128}")
        const val SUPPORTED_GENERAL_PURPOSE_FLAGS = 0x080E
    }
}

class BackupArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)
