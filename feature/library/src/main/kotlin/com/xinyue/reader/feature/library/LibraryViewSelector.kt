package com.xinyue.reader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun LibraryViewSelector(
    selected: LibraryView,
    onSelected: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") scrollable: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.testTag("library-view-selector")) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("library-view-button")
                .semantics {
                    contentDescription = "浏览方式：${selected.label}"
                    stateDescription = "当前浏览：${selected.label}"
                },
        ) {
            Text(selected.label)
            Icon(
                painter = painterResource(XinYueIcons.MoreHorizontal),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            libraryViews.forEach { view ->
                DropdownMenuItem(
                    text = { Text(view.label) },
                    trailingIcon = if (view == selected) {
                        {
                            Icon(
                                painter = painterResource(XinYueIcons.Check),
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(view)
                    },
                    modifier = Modifier
                        .testTag("library-view-${view.name.lowercase()}")
                        .semantics { this.selected = view == selected },
                )
            }
        }
    }
}

internal val LibraryView.label: String
    get() = when (this) {
        LibraryView.BOOKS -> "书籍"
        LibraryView.AUTHORS -> "作者"
        LibraryView.SERIES -> "系列"
        LibraryView.COLLECTIONS -> "集合"
    }

private val libraryViews = LibraryView.entries
