package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun ReaderThemeRow(
    theme: ReaderThemePreset,
    selected: Boolean,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onRename: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(theme.id) { mutableStateOf(false) }
    val selectedStateDescription = if (selected) {
        stringResource(R.string.reader_theme_active)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onApply,
            )
            .semantics {
                selectedStateDescription?.let { stateDescription = it }
            }
            .testTag("reader-theme-apply-${theme.id}")
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            Spacer(Modifier.width(3.dp))
        }
        ThemeColorSwatch(theme)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(theme.name, style = MaterialTheme.typography.titleSmall)
                if (selected) {
                    Text(
                        text = stringResource(R.string.reader_theme_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = if (theme.builtIn) {
                    stringResource(R.string.reader_theme_builtin)
                } else {
                    stringResource(R.string.reader_ui_custom_theme)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("reader-theme-more-${theme.id}"),
            ) {
                Icon(
                    painter = painterResource(XinYueIcons.MoreVert),
                    contentDescription = stringResource(R.string.reader_ui_theme_more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_theme_copy)) },
                    onClick = { menuExpanded = false; onCopy() },
                    modifier = Modifier.testTag("reader-theme-copy-${theme.id}"),
                )
                onUpdate?.let { update ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_theme_update)) },
                        onClick = { menuExpanded = false; update() },
                        modifier = Modifier.testTag("reader-theme-update-${theme.id}"),
                    )
                }
                onRename?.let { rename ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_theme_rename)) },
                        onClick = { menuExpanded = false; rename() },
                        modifier = Modifier.testTag("reader-theme-rename-${theme.id}"),
                    )
                }
                onDelete?.let { delete ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_theme_delete)) },
                        onClick = { menuExpanded = false; delete() },
                        modifier = Modifier.testTag("reader-theme-delete-${theme.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(theme: ReaderThemePreset) {
    val background = Color(theme.settings.backgroundArgb.toInt())
    val foreground = Color(theme.settings.foregroundArgb.toInt())
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("reader-theme-${theme.id}")
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 2) 0.64f else 1f)
                        .height(2.dp)
                        .background(foreground.copy(alpha = 0.82f)),
                )
            }
        }
    }
}
