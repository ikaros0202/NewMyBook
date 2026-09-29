package com.xinyue.reader.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.ui.BookCover
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
fun HomeRoute(
    onOpenBook: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState, onOpenBook, onOpenStatistics, onOpenLibrary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenBook: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("home_root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("新阅") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (uiState.hero == null) {
                item { EmptyHomeCard(onOpenLibrary) }
            } else {
                item {
                    HomeHeroCard(
                        hero = uiState.hero,
                        progress = uiState.heroProgressFraction,
                        onOpen = { onOpenBook(uiState.hero.book.id) },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("阅读统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onOpenStatistics) { Text("查看详情") }
                    }
                    HomeStatisticsPanel(uiState.today, uiState.week, uiState.month)
                }
            }
            if (uiState.recent.isNotEmpty()) {
                item { Text("最近阅读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(uiState.recent, key = Book::id) { book ->
                    RecentBookCard(book = book, onOpen = { onOpenBook(book.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(onOpenLibrary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_empty_hero"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(XinYueIcons.Library),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("开始你的第一本书", style = MaterialTheme.typography.headlineSmall)
            Text(
                "前往书架导入本地 TXT，阅读内容只保存在设备上。",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
            Button(onClick = onOpenLibrary, modifier = Modifier.padding(top = 4.dp)) { Text("前往书架") }
        }
    }
}

@Composable
private fun HomeHeroCard(hero: HomeHeroBook, progress: Double, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("home_hero"),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(hero.book, Modifier.width(84.dp).height(120.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    hero.book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                hero.book.author?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f))
                }
                if (hero.action == HomeBookAction.CONTINUE) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Text("已读 ${(progress.coerceIn(0.0, 1.0) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    when (hero.action) {
                        HomeBookAction.CONTINUE -> "继续阅读"
                        HomeBookAction.START -> "开始阅读"
                        HomeBookAction.REOPEN -> "再次打开"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeStatisticsPanel(
    today: ReadingStatistics,
    week: ReadingStatistics,
    month: ReadingStatistics,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_statistics_panel"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeStatColumn("今天", today, "home_stat_today", Modifier.weight(1f))
            VerticalDivider(Modifier.height(58.dp), color = MaterialTheme.colorScheme.outlineVariant)
            HomeStatColumn("本周", week, "home_stat_week", Modifier.weight(1f))
            VerticalDivider(Modifier.height(58.dp), color = MaterialTheme.colorScheme.outlineVariant)
            HomeStatColumn("本月", month, "home_stat_month", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeStatColumn(
    label: String,
    statistics: ReadingStatistics,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(tag).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatHomeReadingDuration(statistics.activeMillis),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text("${statistics.sessionCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentBookCard(book: Book, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(48.dp).height(68.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.author?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("继续", color = MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun formatHomeReadingDuration(activeMillis: Long): String {
    if (activeMillis <= 0) return "0 分钟"
    val minutes = activeMillis / 60_000L
    if (minutes == 0L) return "<1 分钟"
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours == 0L -> "$minutes 分钟"
        remaining == 0L -> "$hours 小时"
        else -> "$hours 小时 $remaining 分钟"
    }
}
