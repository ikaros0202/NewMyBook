package com.xinyue.reader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.data.BookManager
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookCollectionMembership
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import com.xinyue.reader.core.domain.repository.BookGroupRepository
import com.xinyue.reader.core.domain.repository.BookCoverRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.LibraryLayoutPreferences
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val sections: List<LibrarySection> = emptyList(),
    val view: LibraryView = LibraryView.BOOKS,
    val layoutPreference: LibraryLayoutPreference = LibraryLayoutPreference(),
    val continueBookId: String? = null,
    val progressFractions: Map<String, Double> = emptyMap(),
    val errorMessage: String? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val groups: List<BookGroup> = emptyList(),
    val collectionIdsByBook: Map<String, Set<String>> = emptyMap(),
    val filter: LibraryFilter = LibraryFilter.All,
    val hasAnyBooks: Boolean = false,
    val coverUpdatingBookIds: Set<String> = emptySet(),
    val selectedBookIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val selectionActionInProgress: Boolean = false,
    val pendingDeletion: PendingLibraryDeletion? = null,
    val readingMillisByBook: Map<String, Long> = emptyMap(),
)

data class LibrarySection(
    val key: String,
    val label: String,
    val books: List<Book>,
)

enum class LibraryView {
    BOOKS,
    AUTHORS,
    SERIES,
    COLLECTIONS,
}

internal const val UNCOLLECTED_SECTION_KEY = "collection:uncollected"
private const val UNKNOWN_AUTHOR_SECTION_KEY = "author:unknown"
private const val NO_SERIES_SECTION_KEY = "series:none"

data class PendingLibraryDeletion(
    val bookIds: Set<String>,
    val titles: List<String>,
    val expiresAtEpochMillis: Long,
)

enum class LibrarySort {
    RECENT,
    IMPORTED,
    TITLE,
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: BookRepository,
    private val bookManager: BookManager,
    private val groupRepository: BookGroupRepository,
    private val coverRepository: BookCoverRepository,
    private val importSourceFactory: ImportSourceFactory,
    private val epochClock: EpochClock,
    @param:LibraryStateDispatcher private val stateDispatcher: CoroutineDispatcher,
    private val readingSessionRepository: ReadingSessionRepository? = null,
    private val libraryLayoutPreferences: LibraryLayoutPreferences = DefaultLibraryLayoutPreferences,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState = mutableUiState.asStateFlow()
    private var allBooks: List<Book> = emptyList()
    private var progressFractions: Map<String, Double> = emptyMap()
    private var allGroups: List<BookGroup> = emptyList()
    private var allMemberships: List<BookCollectionMembership> = emptyList()
    private var importState = ImportState()
    private var query = ""
    private var sort = LibrarySort.RECENT
    private var filter: LibraryFilter = LibraryFilter.All
    private var view: LibraryView = LibraryView.BOOKS
    private var coverUpdatingBookIds: Set<String> = emptySet()
    private var selectedBookIds: Set<String> = emptySet()
    private var selectionActionInProgress = false
    private var pendingDeletion: PendingLibraryDeletion? = null
    private var pendingDeletionJob: Job? = null
    private var statisticsJob: Job? = null
    private var readingMillisByBook: Map<String, Long> = emptyMap()
    private var layoutPreference: LibraryLayoutPreference = LibraryLayoutPreference()

    init {
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            repository.observeBooks().collect { books ->
                allBooks = books
                restartBookStatistics()
                selectedBookIds = selectedBookIds.intersect(books.mapTo(mutableSetOf(), Book::id))
                publishState()
            }
        }
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            repository.observeProgress().collect { progress ->
                progressFractions = progress.associate { it.bookId to it.fraction }
                publishState()
            }
        }
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            groupRepository.observeAll().collect { groups ->
                allGroups = groups
                val selectedGroupId = (filter as? LibraryFilter.Group)?.groupId
                if (selectedGroupId != null && groups.none { it.id == selectedGroupId }) {
                    filter = LibraryFilter.All
                }
                publishState()
            }
        }
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            groupRepository.observeMemberships().collect { memberships ->
                allMemberships = memberships
                publishState()
            }
        }
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            libraryLayoutPreferences.observe()
                .catch { cause ->
                    if (cause is CancellationException) throw cause
                    publishState()
                }
                .collect { preference ->
                    layoutPreference = preference
                    publishState()
                }
        }
    }

    private fun restartBookStatistics() {
        statisticsJob?.cancel()
        val statisticsRepository = readingSessionRepository ?: return
        if (allBooks.isEmpty()) {
            readingMillisByBook = emptyMap()
            return
        }
        val observedBooks = allBooks
        statisticsJob = viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            combine(observedBooks.map { statisticsRepository.observeBookStatistics(it.id) }) { stats ->
                stats.mapNotNull { statistic ->
                    statistic.bookId?.let { bookId -> bookId to statistic.activeMillis }
                }.toMap()
            }.collect { values ->
                readingMillisByBook = values
                publishState()
            }
        }
    }

    fun dismissError() {
        importState = importState.copy(errorMessage = null)
        publishState()
    }

    fun setQuery(value: String) {
        query = value
        publishState()
    }

    fun setSort(value: LibrarySort) {
        sort = value
        publishState()
    }

    fun setFilter(value: LibraryFilter) {
        filter = value
        publishState()
    }

    fun setView(value: LibraryView) {
        view = value
        publishState()
    }

    fun setLayoutPreference(preference: LibraryLayoutPreference) {
        if (layoutPreference == preference) return
        viewModelScope.launch(context = stateDispatcher) {
            try {
                libraryLayoutPreferences.set(preference)
                layoutPreference = preference
                publishState()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // A device-local presentation preference must never block the shelf or reader.
            }
        }
    }

    fun createGroup(name: String) {
        runManagementAction(action = { groupRepository.create(name) })
    }

    fun renameGroup(groupId: String, name: String) {
        runManagementAction(action = { groupRepository.rename(groupId, name) })
    }

    fun deleteGroup(groupId: String) {
        runManagementAction(action = { groupRepository.delete(groupId) })
    }

    fun renameBook(bookId: String, title: String) {
        runManagementAction(
            action = { bookManager.rename(bookId, title) },
            onSuccess = {
                allBooks = allBooks.map { if (it.id == bookId) it.copy(title = title.trim()) else it }
            },
        )
    }

    fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String?,
        seriesName: String? = allBooks.firstOrNull { it.id == bookId }?.seriesName,
        seriesOrder: Int? = allBooks.firstOrNull { it.id == bookId }?.seriesOrder,
        collectionIds: Set<String>? = null,
    ) {
        runManagementAction(
            action = {
                bookManager.updateMetadata(bookId, title, author, seriesName, seriesOrder)
                collectionIds?.let { groupRepository.replaceCollectionsForBooks(setOf(bookId), it) }
            },
            onSuccess = {
                allBooks = allBooks.map { book ->
                    if (book.id == bookId) {
                        book.copy(
                            title = title.trim(),
                            author = author?.trim()?.takeIf(String::isNotEmpty),
                            seriesName = seriesName?.trim()?.takeIf(String::isNotEmpty),
                            seriesOrder = seriesOrder,
                        )
                    } else {
                        book
                    }
                }
            },
        )
    }

    fun deleteBook(bookId: String) {
        runManagementAction(
            action = { bookManager.delete(bookId) },
            onSuccess = { allBooks = allBooks.filterNot { it.id == bookId } },
        )
    }

    fun importCover(bookId: String, uriString: String) {
        runCoverAction(bookId) {
            coverRepository.importCover(bookId, importSourceFactory.create(uriString))
        }
    }

    fun clearCover(bookId: String) {
        runCoverAction(bookId) { coverRepository.clearCover(bookId) }
    }

    fun enterSelection(bookId: String) {
        if (allBooks.any { it.id == bookId }) {
            selectedBookIds += bookId
            publishState()
        }
    }

    fun toggleSelection(bookId: String) {
        if (allBooks.none { it.id == bookId }) return
        selectedBookIds = if (bookId in selectedBookIds) selectedBookIds - bookId else selectedBookIds + bookId
        publishState()
    }

    fun selectAllVisible() {
        selectedBookIds = mutableUiState.value.books.mapTo(linkedSetOf(), Book::id)
        publishState()
    }

    fun clearSelection() {
        if (selectionActionInProgress) return
        selectedBookIds = emptySet()
        publishState()
    }

    fun moveSelectedBooks(groupId: String?) {
        runSelectionAction { ids -> groupRepository.moveBooks(ids, groupId) }
    }

    fun addSelectedBooksToCollection(collectionId: String) {
        runSelectionAction { ids -> groupRepository.addBooksToCollections(ids, setOf(collectionId)) }
    }

    fun removeSelectedBooksFromCollection(collectionId: String) {
        runSelectionAction { ids -> groupRepository.removeBooksFromCollections(ids, setOf(collectionId)) }
    }

    fun clearSelectedCollections() {
        runSelectionAction { ids -> groupRepository.replaceCollectionsForBooks(ids, emptySet()) }
    }

    fun markSelectedFinished(finished: Boolean) {
        runSelectionAction { ids -> bookManager.markFinished(ids, finished) }
    }

    fun requestDeleteSelected() {
        if (pendingDeletion != null) {
            importState = importState.copy(errorMessage = "已有待撤销的删除操作")
            publishState()
            return
        }
        if (selectedBookIds.isEmpty() || selectionActionInProgress) return
        val ids = selectedBookIds
        val titles = allBooks.filter { it.id in ids }.map(Book::title)
        val pending = PendingLibraryDeletion(
            bookIds = ids,
            titles = titles,
            expiresAtEpochMillis = epochClock.nowEpochMillis() + DELETE_UNDO_WINDOW_MILLIS,
        )
        pendingDeletion = pending
        selectedBookIds = emptySet()
        publishState()
        pendingDeletionJob = viewModelScope.launch(context = stateDispatcher) {
            delay(DELETE_UNDO_WINDOW_MILLIS)
            commitPendingDeletion(pending)
        }
    }

    fun undoPendingDeletion() {
        val pending = pendingDeletion ?: return
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
        pendingDeletion = null
        val existingIds = allBooks.mapTo(mutableSetOf(), Book::id)
        selectedBookIds = pending.bookIds.intersect(existingIds)
        publishState()
    }

    private suspend fun commitPendingDeletion(pending: PendingLibraryDeletion) {
        if (pendingDeletion !== pending) return
        try {
            bookManager.deleteBatch(pending.bookIds)
            pendingDeletion = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            pendingDeletion = null
            val existingIds = allBooks.mapTo(mutableSetOf(), Book::id)
            selectedBookIds = pending.bookIds.intersect(existingIds)
            importState = importState.copy(errorMessage = "批量删除失败")
        } finally {
            pendingDeletionJob = null
            publishState()
        }
    }

    private fun runSelectionAction(action: suspend (Set<String>) -> Unit) {
        if (selectedBookIds.isEmpty() || selectionActionInProgress) return
        val ids = selectedBookIds
        selectionActionInProgress = true
        publishState()
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                action(ids)
                selectedBookIds = emptySet()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                importState = importState.copy(errorMessage = "批量操作失败")
            } finally {
                selectionActionInProgress = false
                publishState()
            }
        }
    }

    private fun runCoverAction(bookId: String, action: suspend () -> Unit) {
        if (bookId in coverUpdatingBookIds) return
        coverUpdatingBookIds += bookId
        publishState()
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                importState = importState.copy(errorMessage = "封面操作失败")
            } finally {
                coverUpdatingBookIds -= bookId
                publishState()
            }
        }
    }

    private fun runManagementAction(
        action: suspend () -> Unit,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch(context = stateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
                onSuccess()
                publishState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                importState = importState.copy(errorMessage = "操作失败")
                publishState()
            }
        }
    }

    private fun publishState() {
        val persistedCollectionIdsByBook = allMemberships
            .groupBy(BookCollectionMembership::bookId)
            .mapValues { (_, memberships) ->
                memberships.mapTo(linkedSetOf(), BookCollectionMembership::collectionId)
            }
        val collectionIdsByBook = allBooks.associate { book ->
            book.id to (
                persistedCollectionIdsByBook[book.id]
                    ?: book.groupId?.let(::setOf).orEmpty()
                )
        }
        val visibleBooks = allBooks
            .filter { book ->
                filter.matches(
                    book = book,
                    progressFraction = progressFractions[book.id] ?: 0.0,
                    nowEpochMillis = epochClock.nowEpochMillis(),
                    collectionIds = collectionIdsByBook[book.id].orEmpty(),
                )
            }
            .filter { it.matchesLibraryQuery(query) }
            .sortedWith(sort.comparator)
        val continueBookId = allBooks
            .asSequence()
            .filter { book ->
                !book.finished &&
                    book.lastOpenedAtEpochMillis != null &&
                    (progressFractions[book.id] ?: 0.0) > 0.0 &&
                    (progressFractions[book.id] ?: 0.0) < 1.0
            }
            .maxWithOrNull(
                compareBy<Book> { it.lastOpenedAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy(Book::id),
            )
            ?.id
        mutableUiState.value = LibraryUiState(
            books = visibleBooks,
            sections = buildLibrarySections(
                view = view,
                books = visibleBooks,
                collections = allGroups,
                collectionIdsByBook = collectionIdsByBook,
            ),
            view = view,
            layoutPreference = layoutPreference,
            continueBookId = continueBookId,
            progressFractions = progressFractions,
            errorMessage = importState.errorMessage,
            query = query,
            sort = sort,
            groups = allGroups,
            collectionIdsByBook = collectionIdsByBook,
            filter = filter,
            hasAnyBooks = allBooks.isNotEmpty(),
            coverUpdatingBookIds = coverUpdatingBookIds,
            selectedBookIds = selectedBookIds,
            isSelectionMode = selectedBookIds.isNotEmpty(),
            selectionActionInProgress = selectionActionInProgress,
            pendingDeletion = pendingDeletion,
            readingMillisByBook = readingMillisByBook,
        )
    }

    private data class ImportState(
        val errorMessage: String? = null,
    )

    private companion object {
        const val DELETE_UNDO_WINDOW_MILLIS = 8_000L
    }
}

private object DefaultLibraryLayoutPreferences : LibraryLayoutPreferences {
    override fun observe() = flowOf(LibraryLayoutPreference())

    override suspend fun set(preference: LibraryLayoutPreference) = Unit
}

internal fun Book.matchesLibraryQuery(query: String): Boolean {
    val terms = librarySearchTerms(query)
    if (terms.isEmpty()) return true
    return terms.all { term ->
        title.contains(term, ignoreCase = true) ||
            author?.contains(term, ignoreCase = true) == true ||
            seriesName?.contains(term, ignoreCase = true) == true
    }
}

internal fun buildLibrarySections(
    view: LibraryView,
    books: List<Book>,
    collections: List<BookGroup>,
    collectionIdsByBook: Map<String, Set<String>>,
): List<LibrarySection> = when (view) {
    LibraryView.BOOKS -> emptyList()
    LibraryView.AUTHORS -> books
        .groupBy { it.author?.trim()?.takeIf(String::isNotEmpty) }
        .map { (author, authorBooks) ->
            LibrarySection(
                key = author?.let { "author:$it" } ?: UNKNOWN_AUTHOR_SECTION_KEY,
                label = author ?: "未注明作者",
                books = authorBooks,
            )
        }
        .sortedBy { it.label.lowercase() }

    LibraryView.SERIES -> books
        .groupBy { it.seriesName?.trim()?.takeIf(String::isNotEmpty) }
        .map { (seriesName, seriesBooks) ->
            LibrarySection(
                key = seriesName?.let { "series:$it" } ?: NO_SERIES_SECTION_KEY,
                label = seriesName ?: "未加入系列",
                books = seriesBooks.sortedWith(seriesBookComparator),
            )
        }
        .sortedWith(
            compareBy<LibrarySection> { it.key == NO_SERIES_SECTION_KEY }
                .thenBy { it.label.lowercase() },
        )

    LibraryView.COLLECTIONS -> buildList {
        collections.forEach { collection ->
            val collectionBooks = books.filter { book ->
                collection.id in collectionIdsByBook[book.id].orEmpty()
            }
            if (collectionBooks.isNotEmpty()) {
                add(LibrarySection(collection.id, collection.name, collectionBooks))
            }
        }
        val uncollectedBooks = books.filter { collectionIdsByBook[it.id].isNullOrEmpty() }
        if (uncollectedBooks.isNotEmpty()) {
            add(LibrarySection(UNCOLLECTED_SECTION_KEY, "未加入集合", uncollectedBooks))
        }
    }
}

internal fun librarySearchTerms(query: String): List<String> = buildList {
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            add(current.toString())
            current.clear()
        }
    }
    query.forEach { character ->
        if (character.isWhitespace()) flush() else current.append(character)
    }
    flush()
}

private val LibrarySort.comparator: Comparator<Book>
    get() = when (this) {
        LibrarySort.RECENT -> compareByDescending<Book> {
            it.lastOpenedAtEpochMillis ?: it.createdAtEpochMillis
        }.thenBy { it.title.lowercase() }.thenBy(Book::id)

        LibrarySort.IMPORTED -> compareByDescending<Book>(Book::createdAtEpochMillis)
            .thenBy { it.title.lowercase() }
            .thenBy(Book::id)

        LibrarySort.TITLE -> compareBy<Book> { it.title.lowercase() }.thenBy(Book::id)
    }

private val seriesBookComparator = compareBy<Book> { it.seriesOrder == null }
    .thenBy { it.seriesOrder ?: Int.MAX_VALUE }
    .thenBy { it.title.lowercase() }
    .thenBy(Book::id)
