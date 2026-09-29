package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.Book

interface BookManager {
    suspend fun get(bookId: String): Book?
    suspend fun rename(bookId: String, title: String)
    suspend fun updateMetadata(bookId: String, title: String, author: String?)
    suspend fun updateMetadata(
        bookId: String,
        title: String,
        author: String?,
        seriesName: String?,
        seriesOrder: Int?,
    ) {
        updateMetadata(bookId, title, author)
        require(seriesName.isNullOrBlank() && seriesOrder == null) {
            "当前书籍管理器不支持编辑系列信息"
        }
    }
    suspend fun delete(bookId: String)
    suspend fun deleteBatch(bookIds: Set<String>) {
        error("当前书籍管理器不支持批量删除")
    }
    suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
        error("当前书籍管理器不支持完成状态")
    }
}
