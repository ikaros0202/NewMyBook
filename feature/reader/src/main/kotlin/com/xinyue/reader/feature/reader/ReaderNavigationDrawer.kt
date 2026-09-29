package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xinyue.reader.core.domain.model.Bookmark
import com.xinyue.reader.core.text.DetectedChapter
import com.xinyue.reader.core.ui.XinYueIcons

internal enum class ReaderNavigationTab { CHAPTERS, BOOKMARKS }

internal fun filterReaderChapters(
    chapters: List<DetectedChapter>,
    query: String,
): List<DetectedChapter> {
    val normalized = query.trim()
    return if (normalized.isEmpty()) chapters else chapters.filter {
        it.title.contains(normalized, ignoreCase = true)
    }
}

internal fun currentReaderChapter(
    chapters: List<DetectedChapter>,
    anchorOffset: Int,
): DetectedChapter? = chapters.lastOrNull { it.startOffset <= anchorOffset } ?: chapters.firstOrNull()

internal fun sortedReaderBookmarks(bookmarks: List<Bookmark>): List<Bookmark> =
    bookmarks.sortedWith(compareBy(Bookmark::offset, Bookmark::createdAtEpochMillis))

@Composable
internal fun ReaderNavigationDrawer(
    bookTitle: String,
    chapters: List<DetectedChapter>,
    bookmarks: List<Bookmark>,
    anchorOffset: Int,
    content: String,
    contentStartOffset: Int,
    initialTab: ReaderNavigationTab,
    onTabChanged: (ReaderNavigationTab) -> Unit,
    onSelectChapter: (DetectedChapter) -> Unit,
    onSelectBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Bookmark?>(null) }
    val currentChapter = currentReaderChapter(chapters, anchorOffset)
    val filteredChapters = filterReaderChapters(chapters, query)
    val orderedBookmarks = sortedReaderBookmarks(bookmarks)
    val chapterListState = rememberLazyListState()

    LaunchedEffect(selectedTab, filteredChapters, currentChapter) {
        if (selectedTab == ReaderNavigationTab.CHAPTERS) {
            val currentIndex = filteredChapters.indexOf(currentChapter)
            if (currentIndex >= 0) chapterListState.scrollToItem(currentIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepDialogImmersive()
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onDismiss),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f)
                    .widthIn(max = 360.dp)
                    .fillMaxHeight()
                    .testTag("reader-navigation-drawer"),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .clickable(onClick = {}),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        NavigationTabButton(
                            label = "目录",
                            selected = selectedTab == ReaderNavigationTab.CHAPTERS,
                            modifier = Modifier.weight(1f),
                        ) {
                            selectedTab = ReaderNavigationTab.CHAPTERS
                            onTabChanged(selectedTab)
                        }
                        NavigationTabButton(
                            label = "书签",
                            selected = selectedTab == ReaderNavigationTab.BOOKMARKS,
                            modifier = Modifier.weight(1f),
                        ) {
                            selectedTab = ReaderNavigationTab.BOOKMARKS
                            onTabChanged(selectedTab)
                        }
                    }
                    HorizontalDivider()
                    when (selectedTab) {
                        ReaderNavigationTab.CHAPTERS -> {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("搜索目录") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("reader-directory-search"),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { query = "" },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            painterResource(XinYueIcons.MyLocation),
                                            contentDescription = "定位当前章节",
                                        )
                                    }
                                },
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = currentChapter?.title ?: "正文",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (filteredChapters.isEmpty()) {
                                Text(
                                    text = if (chapters.isEmpty()) {
                                        "暂未识别到章节，可从右上角更多进入目录管理。"
                                    } else {
                                        "没有匹配的章节"
                                    },
                                    modifier = Modifier.padding(24.dp),
                                )
                            } else {
                                LazyColumn(
                                    state = chapterListState,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    items(filteredChapters, key = DetectedChapter::startOffset) { chapter ->
                                        val selected = chapter == currentChapter
                                        Text(
                                            text = chapter.title,
                                            modifier = Modifier.fillMaxWidth()
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                )
                                                .clickable { onSelectChapter(chapter) }
                                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                                .testTag("reader-directory-chapter"),
                                            style = if (selected) {
                                                MaterialTheme.typography.titleSmall
                                            } else {
                                                MaterialTheme.typography.bodyLarge
                                            },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }

                        ReaderNavigationTab.BOOKMARKS -> {
                            if (orderedBookmarks.isEmpty()) {
                                Text(
                                    "当前书籍还没有书签，可用阅读菜单右上角的书签图标添加。",
                                    modifier = Modifier.padding(24.dp),
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(orderedBookmarks, key = Bookmark::id) { bookmark ->
                                        val chapter = currentReaderChapter(
                                            chapters,
                                            bookmark.offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { onSelectBookmark(bookmark) }
                                                .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                                        ) {
                                            Text(
                                                chapter?.title ?: "正文",
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                readerBookmarkPreview(
                                                    bookmark,
                                                    content,
                                                    contentStartOffset,
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                TextButton(onClick = { deleteTarget = bookmark }) {
                                                    Text("删除")
                                                }
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除书签？") },
            text = { Text("只删除这条书签，不会修改原始 TXT。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBookmark(bookmark)
                        deleteTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun KeepDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }
}

@Composable
private fun NavigationTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Tab }.padding(vertical = 8.dp),
    ) {
        Text(
            if (selected) "• $label" else label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun readerBookmarkPreview(
    bookmark: Bookmark,
    content: String,
    contentStartOffset: Int,
): String {
    bookmark.note?.takeIf(String::isNotBlank)?.let { return it }
    val localStart = bookmark.offset - contentStartOffset
    if (localStart !in 0 until content.length) return "位置 ${bookmark.offset}"
    val start = localStart.toInt()
    val end = (start + 64).coerceAtMost(content.length)
    return content.substring(start, end).replace('\n', ' ').ifBlank { "位置 ${bookmark.offset}" }
}
