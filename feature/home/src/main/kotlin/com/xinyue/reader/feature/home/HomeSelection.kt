package com.xinyue.reader.feature.home

import com.xinyue.reader.core.domain.model.Book

enum class HomeBookAction {
    CONTINUE,
    START,
    REOPEN,
}

data class HomeHeroBook(
    val book: Book,
    val action: HomeBookAction,
)

data class HomeBookSelection(
    val hero: HomeHeroBook?,
    val recent: List<Book>,
)

fun selectHomeBooks(books: List<Book>): HomeBookSelection {
    val unfinished = books.filterNot(Book::finished)
    val openedUnfinished = unfinished.filter { it.lastOpenedAtEpochMillis != null }
    val hero = when {
        openedUnfinished.isNotEmpty() -> HomeHeroBook(
            book = openedUnfinished.maxWithOrNull(recentBookComparator)!!,
            action = HomeBookAction.CONTINUE,
        )
        unfinished.isNotEmpty() -> HomeHeroBook(
            book = unfinished.maxWithOrNull(importedBookComparator)!!,
            action = HomeBookAction.START,
        )
        books.isNotEmpty() -> HomeHeroBook(
            book = books.maxWithOrNull(recentThenImportedComparator)!!,
            action = HomeBookAction.REOPEN,
        )
        else -> null
    }
    val recent = books.asSequence()
        .filter { it.id != hero?.book?.id && it.lastOpenedAtEpochMillis != null }
        .sortedWith(recentBookComparator.reversed())
        .take(2)
        .toList()
    return HomeBookSelection(hero = hero, recent = recent)
}

private val recentBookComparator = compareBy<Book> { it.lastOpenedAtEpochMillis ?: Long.MIN_VALUE }
    .thenBy(Book::createdAtEpochMillis)
    .thenBy(Book::id)

private val importedBookComparator = compareBy<Book>(Book::createdAtEpochMillis).thenBy(Book::id)

private val recentThenImportedComparator = compareBy<Book> {
    it.lastOpenedAtEpochMillis ?: it.createdAtEpochMillis
}.thenBy(Book::createdAtEpochMillis).thenBy(Book::id)
