package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction

private val readerTapZonePositions = listOf(
    "左上", "上中", "右上",
    "左中", "中央", "右中",
    "左下", "下中", "右下",
)

@Composable
internal fun ReaderTapZoneGrid(
    actions: List<ReaderTapAction>,
    onActionsChange: (List<ReaderTapAction>) -> Unit,
) {
    val normalizedActions = actions.takeIf { it.size == ReaderSettings.TAP_ZONE_COUNT }
        ?: ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val action = normalizedActions[index]
                    val actionLabel = action.readerTapLabel()
                    val zoneDescription = stringResource(
                        R.string.reader_tap_zone_description,
                        index + 1,
                        actionLabel,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button) {
                                val nextActions = normalizedActions.toMutableList()
                                nextActions[index] = action.nextReaderTapAction()
                                onActionsChange(nextActions)
                            }
                            .testTag("reader-tap-zone-${index + 1}")
                            .semantics { contentDescription = zoneDescription }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = readerTapZonePositions[index],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ReaderTapAction.readerTapLabel(): String = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> "上一页"
    ReaderTapAction.NEXT_PAGE -> "下一页"
    ReaderTapAction.MENU -> "菜单"
    ReaderTapAction.NONE -> "无动作"
}

private fun ReaderTapAction.nextReaderTapAction(): ReaderTapAction = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
    ReaderTapAction.NEXT_PAGE -> ReaderTapAction.MENU
    ReaderTapAction.MENU -> ReaderTapAction.NONE
    ReaderTapAction.NONE -> ReaderTapAction.PREVIOUS_PAGE
}
