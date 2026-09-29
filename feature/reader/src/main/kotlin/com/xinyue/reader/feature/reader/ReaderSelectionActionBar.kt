package com.xinyue.reader.feature.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.HighlightColor

@Composable
internal fun ReaderSelectionActionBar(
    onAction: (ReaderSelectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionActionsPaneTitle = stringResource(R.string.reader_selection_actions_label)
    Surface(
        modifier = modifier
            .testTag("reader_selection_actions")
            .semantics { paneTitle = selectionActionsPaneTitle },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            SelectionActionButton(
                labelRes = R.string.reader_bookmark_selection,
                action = ReaderSelectionAction.Bookmark,
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_highlight_yellow,
                action = ReaderSelectionAction.Highlight(HighlightColor.YELLOW),
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_highlight_green,
                action = ReaderSelectionAction.Highlight(HighlightColor.GREEN),
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_highlight_blue,
                action = ReaderSelectionAction.Highlight(HighlightColor.BLUE),
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_highlight_pink,
                action = ReaderSelectionAction.Highlight(HighlightColor.PINK),
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_note_selection,
                action = ReaderSelectionAction.Note,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    @StringRes labelRes: Int,
    action: ReaderSelectionAction,
    onAction: (ReaderSelectionAction) -> Unit,
) {
    TextButton(
        onClick = { onAction(action) },
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(stringResource(labelRes))
    }
}
