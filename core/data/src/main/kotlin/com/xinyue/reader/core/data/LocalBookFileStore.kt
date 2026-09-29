package com.xinyue.reader.core.data

import com.xinyue.reader.core.text.TextStreamNormalizer
import com.xinyue.reader.core.text.TextContentInspector
import com.xinyue.reader.core.text.TxtDecoder
import com.xinyue.reader.core.text.TxtMetadataExtractor
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBookFileStore(
    rootDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    availableBytes: (() -> Long)? = null,
) : BookFileStore {
    private val root = rootDirectory.absoluteFile.normalize()
    private val availableBytesProvider = availableBytes ?: { root.usableSpace }
    private val stagingRoot = root.resolve("import-staging")
    private val booksRoot = root.resolve("books")

    override suspend fun stage(
        bookId: String,
        source: ImportSource,
        preferredCharsetName: String?,
    ): StagedBookFiles = withContext(ioDispatcher) {
        ensureInitialStorage(source.sizeBytes)
        val stageDirectory = safeResolve(stagingRoot, bookId)
        stageDirectory.deleteRecursively()
        check(stageDirectory.mkdirs()) { "无法创建导入暂存目录" }

        try {
            val originalFile = stageDirectory.resolve(ORIGINAL_FILE_NAME)
            val digest = MessageDigest.getInstance("SHA-256")
            val copiedBytes = source.openStream().buffered().use { input ->
                originalFile.outputStream().buffered().use { output ->
                    copyAndDigest(input, output, digest)
                }
            }
            require(copiedBytes > 0) { "TXT 文件不能为空" }
            ensureNormalizationStorage(copiedBytes)

            val sample = originalFile.inputStream().buffered().use { input ->
                input.readAtMost(CHARSET_SAMPLE_BYTES)
            }
            val charsetName = try {
                TxtDecoder.detectCharsetPrefix(sample, preferredCharsetName)
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException(
                    "文件无法按${preferredCharsetName?.let { " $it" }.orEmpty()}解码；请更换编码，或确认它是有效 TXT",
                    error,
                )
            }
            val normalizedFile = stageDirectory.resolve(NORMALIZED_FILE_NAME)
            val offsetIndexFile = stageDirectory.resolve(OFFSET_INDEX_FILE_NAME)
            val contentInspector = TextContentInspector()
            val checkpointWriter = CheckpointingUtf8Writer(
                output = normalizedFile.outputStream().buffered(),
                checkpointIntervalUtf16Units = OFFSET_CHECKPOINT_UTF16_UNITS,
            )
            val decoder = Charset.forName(charsetName).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val contentLength = try {
                originalFile.inputStream().buffered().use { input ->
                    InputStreamReader(input, decoder).use { reader ->
                        checkpointWriter.use { writer ->
                            TextStreamNormalizer.copy(
                                reader = reader,
                                writer = writer,
                                bufferSize = OFFSET_CHECKPOINT_UTF16_UNITS,
                                onNormalizedChunk = contentInspector::accept,
                            )
                        }
                    }
                }
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException(
                    "文件包含无法按 $charsetName 解码的字节，请核对编码",
                    error,
                )
            }
            contentInspector.requireReadableText()
            check(checkpointWriter.utf16Length == contentLength) { "正文偏移索引长度不一致" }
            Utf8OffsetIndexFile.write(offsetIndexFile, checkpointWriter.checkpoints)
            val fallbackTitle = source.displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
            val metadata = TxtMetadataExtractor.extract(
                text = normalizedFile.readFirstChars(METADATA_PREVIEW_UTF16_UNITS),
                fallbackTitle = fallbackTitle,
            )

            StagedBookFiles(
                bookId = bookId,
                originalFileName = source.displayName,
                charsetName = charsetName,
                contentSha256 = digest.digest().toHexString(),
                contentLength = contentLength,
                suggestedTitle = metadata.title,
                suggestedAuthor = metadata.author,
                suggestedSeriesName = metadata.seriesName,
                suggestedSeriesOrder = metadata.seriesOrder,
            )
        } catch (error: Throwable) {
            stageDirectory.deleteRecursively()
            throw error
        }
    }

    override suspend fun commit(staged: StagedBookFiles): StoredBookFiles = withContext(ioDispatcher) {
        val sourceDirectory = safeResolve(stagingRoot, staged.bookId)
        val targetDirectory = safeResolve(booksRoot, staged.bookId)
        check(sourceDirectory.isDirectory) { "导入暂存文件不存在" }
        check(!targetDirectory.exists()) { "书籍私有目录已经存在" }
        targetDirectory.parentFile?.mkdirs()

        try {
            Files.move(sourceDirectory.toPath(), targetDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(sourceDirectory.toPath(), targetDirectory.toPath())
        }

        StoredBookFiles(
            originalPath = targetDirectory.resolve(ORIGINAL_FILE_NAME).relativePath(),
            normalizedPath = targetDirectory.resolve(NORMALIZED_FILE_NAME).relativePath(),
        )
    }

    override suspend fun discard(staged: StagedBookFiles) = withContext(ioDispatcher) {
        safeResolve(stagingRoot, staged.bookId).deleteRecursively()
        Unit
    }

    override suspend fun remove(stored: StoredBookFiles) = withContext(ioDispatcher) {
        val originalDirectory = requireNotNull(safeResolve(root, stored.originalPath).parentFile)
        val normalizedDirectory = requireNotNull(safeResolve(root, stored.normalizedPath).parentFile)
        require(originalDirectory == normalizedDirectory) { "书籍私有文件路径不一致" }
        require(originalDirectory.toPath().startsWith(booksRoot.toPath())) { "非法书籍私有目录" }
        if (originalDirectory.exists() && !originalDirectory.deleteRecursively()) {
            throw IOException("无法清理书籍私有文件，稍后将自动重试")
        }
        Unit
    }

    private fun copyAndDigest(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= ImportBookUseCase.MAX_TXT_BYTES) { "TXT 文件不能超过 50 MB" }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun ensureInitialStorage(sourceSizeBytes: Long) {
        val payloadBytes = if (sourceSizeBytes == ImportSource.UNKNOWN_SIZE_BYTES) {
            0L
        } else {
            sourceSizeBytes * 2L
        }
        require(availableBytesProvider() >= payloadBytes + MIN_FREE_BYTES) {
            "存储空间不足，无法安全导入并保留原始 TXT"
        }
    }

    private fun ensureNormalizationStorage(copiedBytes: Long) {
        require(availableBytesProvider() >= copiedBytes * 2L + MIN_FREE_BYTES) {
            "存储空间不足，无法生成标准化正文"
        }
    }

    private fun safeResolve(parent: File, child: String): File {
        val resolved = parent.resolve(child).absoluteFile.normalize()
        require(resolved.toPath().startsWith(root.toPath())) { "非法私有文件路径" }
        return resolved
    }

    private fun File.relativePath(): String = relativeTo(root).invariantSeparatorsPath

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val bytes = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val count = read(bytes, offset, maxBytes - offset)
            if (count < 0) break
            if (count == 0) {
                val value = read()
                if (value < 0) break
                bytes[offset++] = value.toByte()
            } else {
                offset += count
            }
        }
        return bytes.copyOf(offset)
    }

    private fun File.readFirstChars(maxUnits: Int): String = bufferedReader(Charsets.UTF_8).use { reader ->
        val characters = CharArray(maxUnits)
        val count = reader.read(characters)
        if (count <= 0) "" else String(characters, 0, count)
    }

    private companion object {
        const val ORIGINAL_FILE_NAME = "original.txt"
        const val NORMALIZED_FILE_NAME = "content.txt"
        const val OFFSET_INDEX_FILE_NAME = "offsets.xidx"
        const val CHARSET_SAMPLE_BYTES = 64 * 1024
        const val OFFSET_CHECKPOINT_UTF16_UNITS = 4 * 1024
        const val METADATA_PREVIEW_UTF16_UNITS = 16 * 1024
        const val MIN_FREE_BYTES = 4L * 1024L * 1024L
    }
}
