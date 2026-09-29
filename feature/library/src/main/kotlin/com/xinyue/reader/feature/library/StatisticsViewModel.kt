package com.xinyue.reader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.domain.time.ReadingPeriod
import com.xinyue.reader.core.domain.time.ReadingStatisticsRange
import com.xinyue.reader.core.domain.time.readingStatisticsRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

typealias StatisticsPeriod = ReadingPeriod
typealias StatisticsRange = ReadingStatisticsRange

data class DailyReadingStat(
    val date: LocalDate,
    val activeMillis: Long,
    val sessionCount: Long,
)

data class BookReadingRank(
    val book: Book,
    val statistics: ReadingStatistics,
)

data class StatisticsUiState(
    val period: StatisticsPeriod = StatisticsPeriod.DAY,
    val selectedBookId: String? = null,
    val selectedBook: Book? = null,
    val total: ReadingStatistics = ReadingStatistics(null, 0, 0, 0, 0),
    val daily: List<DailyReadingStat> = emptyList(),
    val ranking: List<BookReadingRank> = emptyList(),
    val clearConfirmationVisible: Boolean = false,
    val isClearing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class StatisticsViewModel internal constructor(
    private val bookRepository: BookRepository,
    private val sessionRepository: ReadingSessionRepository,
    private val clock: EpochClock,
    private val dispatcher: CoroutineDispatcher,
    private val zoneIdProvider: () -> ZoneId,
) : ViewModel() {
    @Inject
    constructor(
        bookRepository: BookRepository,
        sessionRepository: ReadingSessionRepository,
        clock: EpochClock,
        @LibraryStateDispatcher dispatcher: CoroutineDispatcher,
    ) : this(bookRepository, sessionRepository, clock, dispatcher, ZoneId::systemDefault)

    private val mutableUiState = MutableStateFlow(StatisticsUiState())
    val uiState = mutableUiState.asStateFlow()
    private var books: List<Book> = emptyList()
    private var selectedBookId: String? = null
    private var period = StatisticsPeriod.DAY
    private var statisticsJob: Job? = null

    init {
        viewModelScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            bookRepository.observeBooks().collect { observedBooks ->
                books = observedBooks
                if (selectedBookId != null && books.none { it.id == selectedBookId }) selectedBookId = null
                restartStatistics()
            }
        }
        restartStatistics()
    }

    fun selectPeriod(period: StatisticsPeriod) {
        if (this.period == period) return
        this.period = period
        restartStatistics()
    }

    fun selectBook(bookId: String?) {
        if (selectedBookId == bookId) return
        selectedBookId = bookId
        restartStatistics()
    }

    fun requestClear() {
        mutableUiState.value = mutableUiState.value.copy(clearConfirmationVisible = true)
    }

    fun cancelClear() {
        mutableUiState.value = mutableUiState.value.copy(clearConfirmationVisible = false)
    }

    fun confirmClear() {
        if (mutableUiState.value.isClearing) return
        mutableUiState.value = mutableUiState.value.copy(
            clearConfirmationVisible = false,
            isClearing = true,
            errorMessage = null,
        )
        viewModelScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                sessionRepository.clearStatistics()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.value = mutableUiState.value.copy(
                    isClearing = false,
                    errorMessage = "清除阅读统计失败",
                )
                return@launch
            }
            mutableUiState.value = mutableUiState.value.copy(isClearing = false, errorMessage = null)
        }
    }

    fun dismissError() {
        mutableUiState.value = mutableUiState.value.copy(errorMessage = null)
    }

    private fun restartStatistics() {
        statisticsJob?.cancel()
        val range = statisticsRange(clock.nowEpochMillis(), period, zoneIdProvider())
        val selectedBook = books.firstOrNull { it.id == selectedBookId }
        mutableUiState.value = mutableUiState.value.copy(
            period = period,
            selectedBookId = selectedBookId,
            selectedBook = selectedBook,
            total = ReadingStatistics(selectedBookId, 0, 0, range.startEpochMillis, range.endEpochMillis),
            daily = range.localDates.map { DailyReadingStat(it, 0, 0) },
            ranking = emptyList(),
        )
        statisticsJob = viewModelScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            launch { totalFlow(selectedBookId, range).collect { total -> update { copy(total = total) } } }
            launch {
                val flows = range.localDates.map { date ->
                    val day = statisticsRangeForDates(date, date.plusDays(1), zoneIdProvider())
                    totalFlow(selectedBookId, day).asDaily(date)
                }
                combineOrEmpty(flows).collect { daily -> update { copy(daily = daily) } }
            }
            launch {
                val flows = books.map { book ->
                    sessionRepository.observeBookStatistics(book.id, range.startEpochMillis, range.endEpochMillis)
                }
                combineOrEmpty(flows).collect { stats ->
                    val byBook = stats.mapNotNull { statistic ->
                        statistic.bookId?.let { bookId -> bookId to statistic }
                    }.toMap()
                    update { copy(ranking = rankBooks(books, byBook)) }
                }
            }
        }
    }

    private fun totalFlow(bookId: String?, range: StatisticsRange): Flow<ReadingStatistics> =
        if (bookId == null) {
            sessionRepository.observeStatistics(range.startEpochMillis, range.endEpochMillis)
        } else {
            sessionRepository.observeBookStatistics(bookId, range.startEpochMillis, range.endEpochMillis)
        }

    private fun Flow<ReadingStatistics>.asDaily(date: LocalDate): Flow<DailyReadingStat> =
        map { DailyReadingStat(date, it.activeMillis, it.sessionCount) }

    private fun update(transform: StatisticsUiState.() -> StatisticsUiState) {
        mutableUiState.value = mutableUiState.value.transform()
    }
}

internal fun statisticsRange(nowEpochMillis: Long, period: StatisticsPeriod, zoneId: ZoneId): StatisticsRange {
    return readingStatisticsRange(nowEpochMillis, period, zoneId)
}

private fun statisticsRangeForDates(start: LocalDate, end: LocalDate, zoneId: ZoneId): StatisticsRange =
    readingStatisticsRange(start, end, zoneId)

internal fun formatReadingDuration(activeMillis: Long): String {
    if (activeMillis <= 0) return "0 分钟"
    if (activeMillis < 60_000) return "<1 分钟"
    val totalMinutes = activeMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes 分钟"
        minutes == 0L -> "$hours 小时"
        else -> "$hours 小时 $minutes 分钟"
    }
}

internal fun rankBooks(
    books: List<Book>,
    statistics: Map<String, ReadingStatistics>,
): List<BookReadingRank> = books.map { book ->
    BookReadingRank(book, statistics[book.id] ?: ReadingStatistics(book.id, 0, 0, 0, 0))
}.sortedWith(
    compareByDescending<BookReadingRank> { it.statistics.activeMillis }
        .thenBy { it.book.title.lowercase() }
        .thenBy { it.book.id },
)

@Suppress("UNCHECKED_CAST")
private fun <T> combineOrEmpty(flows: List<Flow<T>>): Flow<List<T>> =
    if (flows.isEmpty()) {
        kotlinx.coroutines.flow.flowOf(emptyList())
    } else {
        combine(flows.map { flow -> flow.map { value -> value as Any? } }) { values ->
            values.map { it as T }
        }
    }
