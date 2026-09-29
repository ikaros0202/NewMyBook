package com.xinyue.reader.core.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.CodingErrorAction

data class Utf8OffsetCheckpoint(
    val utf16Offset: Long,
    val utf8ByteOffset: Long,
)

/**
 * Writes valid UTF-8 while recording bounded UTF-16-to-byte seek checkpoints.
 * A trailing high surrogate is held until the next chunk so no checkpoint can
 * point into the middle of one Unicode code point.
 */
class CheckpointingUtf8Writer(
    output: OutputStream,
    private val checkpointIntervalUtf16Units: Int,
) : Writer() {
    private val countingOutput = CountingOutputStream(output)
    private val delegate = OutputStreamWriter(countingOutput, Charsets.UTF_8)
    private val mutableCheckpoints = mutableListOf(Utf8OffsetCheckpoint(0, 0))
    private var utf16Units = 0L
    private var nextCheckpoint = checkpointIntervalUtf16Units.toLong()
    private var pendingHighSurrogate: Char? = null
    private var closed = false

    val checkpoints: List<Utf8OffsetCheckpoint> get() = mutableCheckpoints.toList()
    val utf16Length: Long get() = utf16Units

    init {
        require(checkpointIntervalUtf16Units > 0) { "checkpoint interval must be positive" }
    }

    override fun write(characters: CharArray, offset: Int, count: Int) {
        check(!closed) { "writer is closed" }
        require(offset >= 0 && count >= 0 && offset + count <= characters.size)
        if (count == 0) return

        var start = offset
        var remaining = count
        pendingHighSurrogate?.let { high ->
            val low = characters[start]
            require(low.isLowSurrogate()) { "正文包含不完整的 Unicode 代理对" }
            delegate.write(charArrayOf(high, low), 0, 2)
            utf16Units += 2
            pendingHighSurrogate = null
            start += 1
            remaining -= 1
        }

        if (remaining > 0 && characters[start + remaining - 1].isHighSurrogate()) {
            pendingHighSurrogate = characters[start + remaining - 1]
            remaining -= 1
        }
        if (remaining > 0) {
            delegate.write(characters, start, remaining)
            utf16Units += remaining
        }
        flushAndCheckpoint()
    }

    override fun write(character: Int) {
        write(charArrayOf(character.toChar()), 0, 1)
    }

    override fun flush() {
        check(!closed) { "writer is closed" }
        flushAndCheckpoint(forceRecord = false)
    }

    override fun close() {
        if (closed) return
        pendingHighSurrogate?.let {
            delegate.write('\uFFFD'.code)
            utf16Units += 1
            pendingHighSurrogate = null
        }
        flushAndCheckpoint(forceRecord = true)
        delegate.close()
        closed = true
    }

    private fun flushAndCheckpoint(forceRecord: Boolean = false) {
        delegate.flush()
        if (forceRecord || utf16Units >= nextCheckpoint) {
            val checkpoint = Utf8OffsetCheckpoint(utf16Units, countingOutput.count)
            if (mutableCheckpoints.last() != checkpoint) mutableCheckpoints += checkpoint
            nextCheckpoint = ((utf16Units / checkpointIntervalUtf16Units) + 1L) *
                checkpointIntervalUtf16Units
        }
    }
}

object Utf8OffsetIndexFile {
    fun write(file: File, checkpoints: List<Utf8OffsetCheckpoint>) {
        validate(checkpoints)
        file.outputStream().buffered().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeInt(checkpoints.size)
                checkpoints.forEach { checkpoint ->
                    data.writeLong(checkpoint.utf16Offset)
                    data.writeLong(checkpoint.utf8ByteOffset)
                }
            }
        }
    }

    fun read(file: File): List<Utf8OffsetCheckpoint> = file.inputStream().buffered().use { input ->
        DataInputStream(input).use { data ->
            require(data.readInt() == MAGIC) { "无效的正文偏移索引" }
            require(data.readInt() == VERSION) { "不支持的正文偏移索引版本" }
            val count = data.readInt()
            require(count in 1..MAX_CHECKPOINTS) { "正文偏移索引记录数无效" }
            List(count) {
                Utf8OffsetCheckpoint(
                    utf16Offset = data.readLong(),
                    utf8ByteOffset = data.readLong(),
                )
            }.also(::validate)
        }
    }

    private fun validate(checkpoints: List<Utf8OffsetCheckpoint>) {
        require(checkpoints.isNotEmpty() && checkpoints.first() == Utf8OffsetCheckpoint(0, 0)) {
            "正文偏移索引必须从 0 开始"
        }
        checkpoints.zipWithNext().forEach { (before, after) ->
            require(
                after.utf16Offset > before.utf16Offset &&
                    after.utf8ByteOffset > before.utf8ByteOffset,
            ) { "正文偏移索引必须严格递增" }
        }
    }

    private const val MAGIC = 0x58494458 // XIDX
    private const val VERSION = 1
    private const val MAX_CHECKPOINTS = 1_000_000
}

object Utf8OffsetIndexBuilder {
    fun build(
        normalizedUtf8File: File,
        targetIndexFile: File,
        checkpointIntervalUtf16Units: Int = DEFAULT_CHECKPOINT_INTERVAL,
    ) {
        require(normalizedUtf8File.isFile) { "书籍正文文件不存在" }
        targetIndexFile.parentFile?.mkdirs()
        val temporary = targetIndexFile.resolveSibling("${targetIndexFile.name}.tmp")
        temporary.delete()
        val writer = CheckpointingUtf8Writer(DiscardingOutputStream, checkpointIntervalUtf16Units)
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            normalizedUtf8File.inputStream().buffered().use { input ->
                InputStreamReader(input, decoder).use { reader ->
                    writer.use { indexed ->
                        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (count > 0) indexed.write(buffer, 0, count)
                        }
                    }
                }
            }
            Utf8OffsetIndexFile.write(temporary, writer.checkpoints)
            try {
                Files.move(
                    temporary.toPath(),
                    targetIndexFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    targetIndexFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private const val DEFAULT_CHECKPOINT_INTERVAL = 4 * 1024
}

private object DiscardingOutputStream : OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
}

private class CountingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        delegate.write(value)
        count += 1
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        delegate.write(bytes, offset, length)
        count += length
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
