package com.xinyue.reader.feature.library

import com.xinyue.reader.core.domain.model.Book

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Recent : LibraryFilter
    data object Unread : LibraryFilter
    data object Reading : LibraryFilter
    data object Finished : LibraryFilter
    data object Uncollected : LibraryFilter
    data class Group(val groupId: String) : LibraryFilter
}

internal const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1_000L

internal fun LibraryFilter.matches(
    book: Book,
    progressFraction: Double,
    nowEpochMillis: Long,
    collectionIds: Set<String> = book.groupId?.let(::setOf).orEmpty(),
): Boolean {
    val safeProgress = progressFraction.coerceIn(0.0, 1.0)
    val effectivelyFinished = book.finished || safeProgress >= 1.0
    return when (this) {
        LibraryFilter.All -> true
        LibraryFilter.Recent -> book.lastOpenedAtEpochMillis?.let { lastOpened ->
            lastOpened in (nowEpochMillis - THIRTY_DAYS_MILLIS)..nowEpochMillis
        } ?: false
        LibraryFilter.Unread -> safeProgress <= 0.0 && !effectivelyFinished
        LibraryFilter.Reading -> safeProgress > 0.0 && !effectivelyFinished
        LibraryFilter.Finished -> effectivelyFinished
        LibraryFilter.Uncollected -> collectionIds.isEmpty()
        is LibraryFilter.Group -> groupId in collectionIds
    }
}
