package com.xinyue.reader.core.domain.model

enum class BookSearchIndexStatus { NOT_INDEXED, BUILDING, READY, ERROR }

data class BookSearchIndexState(
    val bookId: String,
    val status: BookSearchIndexStatus,
    val indexedUtf16Length: Long,
    val errorMessage: String? = null,
)

data class BookSearchResult(
    val offset: Long,
    val endOffset: Long,
    val chapterStartOffset: Long?,
    val snippet: String,
    val highlightStart: Int,
    val highlightEnd: Int,
)
