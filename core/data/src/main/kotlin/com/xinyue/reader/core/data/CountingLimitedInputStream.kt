package com.xinyue.reader.core.data

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class CountingLimitedInputStream(
    input: InputStream,
    private val limit: Long,
) : FilterInputStream(input) {
    init {
        require(limit >= 0) { "读取限制无效" }
    }

    var count: Long = 0
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) addCount(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) addCount(read.toLong())
        return read
    }

    private fun addCount(read: Long) {
        count += read
        if (count > limit) throw BackupLimitExceededException()
    }
}

class BackupLimitExceededException : IOException("备份数据超出安全限制")
