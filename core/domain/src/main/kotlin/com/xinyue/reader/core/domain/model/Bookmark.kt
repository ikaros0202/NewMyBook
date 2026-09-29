package com.xinyue.reader.core.domain.model

data class Bookmark(
    val id: String,
    val bookId: String,
    val offset: Long,
    val note: String?,
    val createdAtEpochMillis: Long,
)
