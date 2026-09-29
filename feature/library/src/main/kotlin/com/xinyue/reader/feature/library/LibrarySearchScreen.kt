package com.xinyue.reader.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun LibrarySearchScreen(
    uiState: LibraryUiState,
    fieldModifier: Modifier,
    listState: LazyListState,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
    bookItem: @Composable (Book) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.testTag("library_search_root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("library_search_back"),
                ) {
                    Icon(
                        painter = painterResource(XinYueIcons.ArrowBack),
                        contentDescription = "返回书架",
                    )
                }
                TextField(
                    value = uiState.query,
                    onValueChange = onQueryChanged,
                    placeholder = { Text("搜索书名、作者或系列") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(XinYueIcons.Search),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (uiState.query.isBlank()) {
                        null
                    } else {
                        {
                            IconButton(
                                onClick = { onQueryChanged("") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("library_search_clear"),
                            ) {
                                Icon(
                                    painter = painterResource(XinYueIcons.Close),
                                    contentDescription = "清除搜索",
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = fieldModifier
                        .weight(1f)
                        .testTag("library_search_field"),
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = when {
                        uiState.query.isBlank() -> "输入书名、作者或系列开始搜索"
                        uiState.books.isEmpty() -> "没有找到相关书籍，换个书名、作者或系列试试"
                        else -> "找到 ${uiState.books.size} 本"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("library_search_count"),
                )
            }
            if (uiState.query.isNotBlank()) {
                items(uiState.books, key = Book::id) { book -> bookItem(book) }
            }
        }
    }
}
