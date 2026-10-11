package com.xinyue.reader.feature.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
        FlowRow(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SelectionActionButton(
                labelRes = R.string.reader_bookmark_selection,
                action = ReaderSelectionAction.Bookmark,
                onAction = onAction,
            )
            SelectionActionButton(
                labelRes = R.string.reader_note_selection,
                action = ReaderSelectionAction.Note,
                onAction = onAction,
            )
            HighlightActionButton(
                labelRes = R.string.reader_highlight_yellow,
                color = HighlightColor.YELLOW,
                onAction = onAction,
            )
            HighlightActionButton(
                labelRes = R.string.reader_highlight_green,
                color = HighlightColor.GREEN,
                onAction = onAction,
            )
            HighlightActionButton(
                labelRes = R.string.reader_highlight_blue,
                color = HighlightColor.BLUE,
                onAction = onAction,
            )
            HighlightActionButton(
                labelRes = R.string.reader_highlight_pink,
                color = HighlightColor.PINK,
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

@Composable
private fun HighlightActionButton(
    @androidx.annotation.StringRes labelRes: Int,
    color: HighlightColor,
    onAction: (ReaderSelectionAction) -> Unit,
) {
    val label = stringResource(labelRes)
    Surface(
        onClick = { onAction(ReaderSelectionAction.Highlight(color)) },
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(color.toHighlightArgb()), CircleShape),
            )
        }
    }
}
