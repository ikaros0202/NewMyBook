package com.xinyue.reader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.ui.BookCover

@Composable
fun StatisticsRoute(
    bookId: String?,
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.selectBook(bookId) }
    StatisticsScreen(
        uiState = uiState,
        onBack = onBack,
        showBackButton = statisticsShowsBackButton(bookId),
        onSelectPeriod = viewModel::selectPeriod,
        onSelectBook = viewModel::selectBook,
        onRequestClear = viewModel::requestClear,
        onConfirmClear = viewModel::confirmClear,
        onCancelClear = viewModel::cancelClear,
        onDismissError = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onBack: () -> Unit,
    onSelectPeriod: (StatisticsPeriod) -> Unit,
    onSelectBook: (String?) -> Unit,
    onRequestClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onDismissError: () -> Unit,
    showBackButton: Boolean = true,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        onDismissError()
    }
    Scaffold(
        modifier = Modifier.testTag("statistics_root"),
        topBar = {
            TopAppBar(
                title = { Text(uiState.selectedBook?.let { "《${it.title}》统计" } ?: "阅读统计") },
                navigationIcon = {
                    if (showBackButton) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("statistics_navigation_back"),
                        ) { Text("返回") }
                    }
                },
                actions = { TextButton(onClick = onRequestClear) { Text("清除") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("statistics-summary")
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = "有效阅读 ${formatReadingDuration(uiState.total.activeMillis)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${uiState.total.sessionCount} 次阅读会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val recent = uiState.daily.lastOrNull { it.activeMillis > 0 }
                    Text(
                        text = recent?.let { "最近阅读：${it.date} · ${formatReadingDuration(it.activeMillis)}" }
                            ?: "当前时段暂无阅读记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("statistics-daily-panel"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("每日有效阅读", style = MaterialTheme.typography.titleMedium)
                        StatisticsPeriodSelector(
                            period = uiState.period,
                            onSelectPeriod = onSelectPeriod,
                        )
                    }
                    StatisticsChart(uiState.daily, Modifier.padding(top = 12.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            if (uiState.selectedBookId != null) {
                item { TextButton(onClick = { onSelectBook(null) }) { Text("查看全部书籍") } }
            }
            item { Text("书籍排行", style = MaterialTheme.typography.titleMedium) }
            items(uiState.ranking, key = { it.book.id }) { item ->
                Surface(
                    onClick = { onSelectBook(item.book.id) },
                    modifier = Modifier.fillMaxWidth().testTag("statistics-book-${item.book.id}"),
                    shape = RectangleShape,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BookCover(item.book, Modifier.width(40.dp).height(60.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.book.author?.takeIf(String::isNotBlank)?.let { author ->
                                    Text(
                                        text = author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = formatReadingDuration(item.statistics.activeMillis),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }

    if (uiState.clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            title = { Text("清除本机阅读统计？") },
            text = { Text("只清除有效阅读时长和会话次数，不会删除书籍、阅读进度、书签、高亮或笔记。") },
            confirmButton = {
                Button(onClick = onConfirmClear, enabled = !uiState.isClearing) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = onCancelClear) { Text("取消") } },
        )
    }
}

@Composable
private fun StatisticsPeriodSelector(
    period: StatisticsPeriod,
    onSelectPeriod: (StatisticsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .width(144.dp)
            .testTag("statistics-periods"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatisticsPeriod.entries.forEach { candidate ->
            val label = when (candidate) {
                StatisticsPeriod.DAY -> "日"
                StatisticsPeriod.WEEK -> "周"
                StatisticsPeriod.MONTH -> "月"
            }
            Surface(
                onClick = { onSelectPeriod(candidate) },
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RectangleShape,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { selected = candidate == period }
                    .testTag("statistics-period-${candidate.name.lowercase()}"),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        color = if (candidate == period) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(top = 2.dp),
                    ) {
                        if (candidate == period) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun statisticsShowsBackButton(bookId: String?): Boolean = bookId != null
