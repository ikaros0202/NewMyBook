package com.xinyue.reader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.domain.time.ReadingPeriod
import com.xinyue.reader.core.domain.time.readingStatisticsRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val hero: HomeHeroBook? = null,
    val heroProgressFraction: Double = 0.0,
    val recent: List<Book> = emptyList(),
    val today: ReadingStatistics = emptyStatistics(),
    val week: ReadingStatistics = emptyStatistics(),
    val month: ReadingStatistics = emptyStatistics(),
)

private fun emptyStatistics() = ReadingStatistics(
    bookId = null,
    activeMillis = 0,
    sessionCount = 0,
    rangeStartEpochMillis = 0,
    rangeEndEpochMillis = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val sessionRepository: ReadingSessionRepository,
    private val clock: EpochClock,
    @HomeStateDispatcher private val dispatcher: CoroutineDispatcher,
    @HomeZoneId private val zoneId: ZoneId,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    internal constructor(
        bookRepository: BookRepository,
        sessionRepository: ReadingSessionRepository,
        clock: EpochClock,
        dispatcher: CoroutineDispatcher,
        zoneIdProvider: () -> ZoneId,
    ) : this(bookRepository, sessionRepository, clock, dispatcher, zoneIdProvider())

    init {
        observe(zoneId)
    }

    private var observing = false

    private fun observe(zoneId: ZoneId) {
        if (observing) return
        observing = true
        viewModelScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            combine(bookRepository.observeBooks(), bookRepository.observeProgress()) { books, progress ->
                val selection = selectHomeBooks(books)
                val heroProgress = selection.hero?.book?.id
                    ?.let { heroId -> progress.firstOrNull { it.bookId == heroId }?.fraction }
                    ?: 0.0
                selection to heroProgress
            }.catch { emit(HomeBookSelection(null, emptyList()) to 0.0) }
                .collect { (selection, heroProgress) ->
                    mutableUiState.value = mutableUiState.value.copy(
                        hero = selection.hero,
                        heroProgressFraction = heroProgress,
                        recent = selection.recent,
                    )
                }
        }
        ReadingPeriod.entries.forEach { period ->
            viewModelScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                val range = readingStatisticsRange(clock.nowEpochMillis(), period, zoneId)
                sessionRepository.observeStatistics(range.startEpochMillis, range.endEpochMillis)
                    .catch { emit(emptyStatistics()) }
                    .collect { statistics ->
                        mutableUiState.value = when (period) {
                            ReadingPeriod.DAY -> mutableUiState.value.copy(today = statistics)
                            ReadingPeriod.WEEK -> mutableUiState.value.copy(week = statistics)
                            ReadingPeriod.MONTH -> mutableUiState.value.copy(month = statistics)
                        }
                    }
            }
        }
    }
}
