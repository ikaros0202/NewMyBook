package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupManifestEntry

object BackupSafetyLimits {
    const val MAX_ENTRY_COUNT = 10_000
    const val MAX_TEXT_BYTES = 50L * 1024 * 1024
    const val MAX_FONT_BYTES = 64L * 1024 * 1024
    const val MAX_COVER_BYTES = 20L * 1024 * 1024
    const val MAX_CATALOG_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 200L

    fun requireEntryCount(count: Int) {
        require(count in 0..MAX_ENTRY_COUNT) { "备份条目数量超出限制" }
    }

    fun requireEntrySize(kind: BackupEntryKind, size: Long) {
        val limit = when (kind) {
            BackupEntryKind.CATALOG, BackupEntryKind.OFFSET_INDEX -> MAX_CATALOG_BYTES
            BackupEntryKind.ORIGINAL_TEXT, BackupEntryKind.NORMALIZED_TEXT -> MAX_TEXT_BYTES
            BackupEntryKind.CUSTOM_COVER -> MAX_COVER_BYTES
            BackupEntryKind.FONT -> MAX_FONT_BYTES
        }
        require(size in 0..limit) { "备份条目大小超出限制" }
    }

    fun requireTotalExpandedBytes(bytes: Long) {
        require(bytes in 0..MAX_TOTAL_EXPANDED_BYTES) { "备份展开总大小超出限制" }
    }

    fun requireCompressionRatio(compressedSize: Long, uncompressedSize: Long) {
        require(compressedSize >= 0 && uncompressedSize >= 0) { "备份条目大小无效" }
        if (uncompressedSize == 0L) return
        require(compressedSize > 0) { "备份条目压缩比无效" }
        require(uncompressedSize <= compressedSize * MAX_COMPRESSION_RATIO) { "备份条目压缩比超出限制" }
    }

    fun requireManifestEntries(entries: List<BackupManifestEntry>) {
        requireEntryCount(entries.size)
        var total = 0L
        entries.forEach { entry ->
            requireEntrySize(entry.kind, entry.uncompressedSize)
            require(entry.uncompressedSize <= MAX_TOTAL_EXPANDED_BYTES - total) { "备份展开总大小超出限制" }
            total += entry.uncompressedSize
        }
        requireTotalExpandedBytes(total)
    }
}
