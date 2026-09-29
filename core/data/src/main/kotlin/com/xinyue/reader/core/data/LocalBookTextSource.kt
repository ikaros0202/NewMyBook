package com.xinyue.reader.core.data

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalTextSource(
    rootDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextSource {
    private val root = rootDirectory.absoluteFile.normalize()
    private val indexRepairMutex = Mutex()

    override suspend fun readWindow(
        normalizedPath: String,
        anchorOffset: Long,
        beforeUtf16Units: Int,
        afterUtf16Units: Int,
    ): TextWindow = withContext(ioDispatcher) {
        require(beforeUtf16Units >= 0 && afterUtf16Units > 0) { "正文窗口大小无效" }
        val textFile = safeResolve(normalizedPath)
        require(textFile.isFile) { "书籍正文文件不存在" }
        val indexFile = textFile.resolveSibling(OFFSET_INDEX_FILE_NAME)
        val checkpoints = loadOrRepairIndex(textFile, indexFile)
        val totalLength = checkpoints.last().utf16Offset
        val safeAnchor = anchorOffset.coerceIn(0, totalLength)
        val requestedStart = (safeAnchor - beforeUtf16Units).coerceAtLeast(0)
        val requestedEnd = (safeAnchor + afterUtf16Units).coerceAtMost(totalLength)
        val checkpoint = checkpoints.last { it.utf16Offset <= requestedStart }

        FileInputStream(textFile).use { input ->
            input.channel.position(checkpoint.utf8ByteOffset)
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                var remainingSkip = requestedStart - checkpoint.utf16Offset
                var lastSkipped = -1
                while (remainingSkip > 0) {
                    lastSkipped = reader.read()
                    if (lastSkipped < 0) break
                    remainingSkip -= 1
                }

                var actualStart = requestedStart
                val result = StringBuilder((requestedEnd - requestedStart).toInt() + 1)
                if (lastSkipped >= 0 && lastSkipped.toChar().isHighSurrogate()) {
                    actualStart -= 1
                    result.append(lastSkipped.toChar())
                }
                val wantedUnits = (requestedEnd - actualStart).toInt()
                val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                while (result.length < wantedUnits) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, wantedUnits - result.length))
                    if (count < 0) break
                    if (count == 0) continue
                    result.append(buffer, 0, count)
                }
                if (result.isNotEmpty() && result.last().isHighSurrogate()) {
                    val low = reader.read()
                    if (low >= 0 && low.toChar().isLowSurrogate()) result.append(low.toChar())
                }
                TextWindow(
                    startOffset = actualStart,
                    text = result.toString(),
                    totalUtf16Length = totalLength,
                )
            }
        }
    }

    private suspend fun loadOrRepairIndex(
        textFile: File,
        indexFile: File,
    ): List<Utf8OffsetCheckpoint> = indexRepairMutex.withLock {
        val existing = runCatching { Utf8OffsetIndexFile.read(indexFile) }.getOrNull()
        if (existing != null && existing.last().utf8ByteOffset == textFile.length()) {
            return@withLock existing
        }

        Utf8OffsetIndexBuilder.build(textFile, indexFile)
        Utf8OffsetIndexFile.read(indexFile).also { rebuilt ->
            require(rebuilt.last().utf8ByteOffset == textFile.length()) {
                "正文偏移索引与正文不一致"
            }
        }
    }

    private fun safeResolve(relativePath: String): File {
        val file = root.resolve(relativePath).absoluteFile.normalize()
        require(file.toPath().startsWith(root.toPath())) { "非法书籍文件路径" }
        return file
    }

    private companion object {
        const val OFFSET_INDEX_FILE_NAME = "offsets.xidx"
    }
}
