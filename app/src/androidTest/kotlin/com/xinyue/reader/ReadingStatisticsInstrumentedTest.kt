package com.xinyue.reader

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.model.splitEffectiveReadingInterval
import com.xinyue.reader.feature.library.BookReadingRank
import com.xinyue.reader.feature.library.DailyReadingStat
import com.xinyue.reader.feature.library.StatisticsPeriod
import com.xinyue.reader.feature.library.StatisticsScreen
import com.xinyue.reader.feature.library.StatisticsUiState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStatisticsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fiveMinuteCapPerBookPeriodTabsAccessibleChartAndProtectedClearRenderTogether() {
        val activeMillis = splitEffectiveReadingInterval(0, 10 * 60_000L, ZoneId.of("UTC"))
            .sumOf { it.activeMillis }
        assertEquals(5 * 60_000L, activeMillis)
        val selectedPeriods = mutableListOf<StatisticsPeriod>()
        var clearConfirmed = false
        val date = LocalDate.of(2026, 7, 16)
        val book = book()
        val statistic = ReadingStatistics(book.id, activeMillis, 1, 0, 24 * 60 * 60_000L)
        compose.activity.setContent {
            StatisticsScreen(
                uiState = StatisticsUiState(
                    period = StatisticsPeriod.DAY,
                    selectedBookId = book.id,
                    selectedBook = book,
                    total = statistic,
                    daily = listOf(DailyReadingStat(date, activeMillis, 1)),
                    ranking = listOf(BookReadingRank(book, statistic)),
                    clearConfirmationVisible = true,
                ),
                onBack = {},
                onSelectPeriod = { selectedPeriods += it },
                onSelectBook = {},
                onRequestClear = {},
                onConfirmClear = { clearConfirmed = true },
                onCancelClear = {},
                onDismissError = {},
            )
        }

        compose.onNodeWithText("有效阅读 5 分钟").assertIsDisplayed()
        compose.onNodeWithTag("statistics_navigation_back").assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-07-16，5 分钟，1 次会话").assertIsDisplayed()
        val chart = compose.onNodeWithContentDescription(
            "每日有效阅读时长图表，共 1 个数据桶。2026-07-16，5 分钟，1 次会话；",
        )
        assertEquals(184f * compose.density.density, chart.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithText("不会删除书籍、阅读进度、书签、高亮或笔记。", substring = true).assertIsDisplayed()
        compose.onNodeWithText("确认清除").performClick()
        assertTrue(clearConfirmed)
    }

    private fun book() = Book(
        id = "book-1",
        title = "公开测试书",
        author = null,
        originalFileName = "public-fixture.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-fixture",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
