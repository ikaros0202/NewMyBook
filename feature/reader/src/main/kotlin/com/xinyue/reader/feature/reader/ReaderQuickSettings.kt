package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.ui.XinYueIcons
import java.util.Locale

/** Compact draft controls; the caller owns preview, persistence and advanced settings. */
@Composable
internal fun ReaderQuickSettings(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    scope: ReaderSettingsScope,
    onScopeChanged: (ReaderSettingsScope) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onMoreSettings: () -> Unit,
) {
    val safe = settings.normalized()
    val fontSizeDescription = stringResource(R.string.reader_font_size)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("reader-quick-settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReaderScopeSelector(
            scope = scope,
            onScopeChanged = onScopeChanged,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("reader-quick-settings-font-size-slider")
                .semantics(mergeDescendants = true) {
                    contentDescription = fontSizeDescription
                    stateDescription = safe.fontSizeSp.formatSp()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("字号", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = {
                    onSettingsChange(safe.copy(fontSizeSp = (safe.fontSizeSp - 1f).coerceAtLeast(14f)))
                },
                enabled = safe.fontSizeSp > 14f,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "减小字号" }
                    .testTag("reader-quick-settings-font-size-decrease"),
            ) { Text("A−", style = MaterialTheme.typography.titleMedium) }
            Text(
                text = safe.fontSizeSp.formatSp(),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = {
                    onSettingsChange(safe.copy(fontSizeSp = (safe.fontSizeSp + 1f).coerceAtMost(36f)))
                },
                enabled = safe.fontSizeSp < 36f,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "增大字号" }
                    .testTag("reader-quick-settings-font-size-increase"),
            ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reader-quick-settings-theme-brightness"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ReaderColorTheme.entries.forEach { theme ->
                QuickThemeChoice(
                    theme = theme,
                    label = stringResource(theme.quickLabelResource()),
                    selected = safe.matchesBuiltInPalette(theme),
                    tag = "reader-quick-settings-theme-${theme.name.lowercase(Locale.ROOT)}",
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChange(safe.withBuiltInPalette(theme)) },
                )
            }
        }
        if (scope == ReaderSettingsScope.GLOBAL) {
            ReaderBrightnessControl(
                settings = safe,
                onSettingsChange = onSettingsChange,
            )
        } else {
            Text(
                stringResource(R.string.reader_quick_global_note),
                modifier = Modifier.testTag("reader-quick-settings-global-only-note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onMoreSettings)
                .testTag("reader-quick-settings-more"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "更多阅读设置",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                painter = painterResource(XinYueIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shared brightness row for quick and full/global reader settings. */
@Composable
internal fun ReaderBrightnessControl(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val brightness = settings.brightness
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("reader-quick-settings-brightness"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("亮度", style = MaterialTheme.typography.bodyMedium)
        if (brightness < 0f) {
            Text(
                "跟随系统",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ReaderSettingSlider(
                value = brightness,
                onValueChange = { onSettingsChange(settings.copy(brightness = it)) },
                valueRange = 0.02f..1f,
                steps = 0,
                contentDescription = "亮度，${(brightness * 100).toInt()}%",
                modifier = Modifier
                    .weight(1f)
                    .testTag("reader-quick-settings-brightness-slider"),
            )
        }
        TextButton(
            onClick = {
                onSettingsChange(settings.copy(brightness = if (brightness < 0f) 0.5f else -1f))
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("reader-quick-settings-brightness-toggle")
                .semantics {
                    stateDescription = if (brightness < 0f) "跟随系统" else "自定义亮度"
                },
        ) {
            Text(if (brightness < 0f) "自定义" else "跟随系统")
        }
    }
}

/**
 * The scope control is shared by the compact and full appearance surfaces so both use the
 * same vocabulary, hit target, and menu semantics.
 */
@Composable
internal fun ReaderScopeSelector(
    scope: ReaderSettingsScope,
    onScopeChanged: (ReaderSettingsScope) -> Unit,
    modifier: Modifier = Modifier,
    includeCurrentBook: Boolean = true,
    testTagPrefix: String = "reader-quick-settings-scope",
) {
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    val scopeLabel = if (scope == ReaderSettingsScope.GLOBAL) {
        stringResource(R.string.reader_scope_global)
    } else {
        stringResource(R.string.reader_scope_book)
    }
    val selectedTag = if (scope == ReaderSettingsScope.GLOBAL) {
        "$testTagPrefix-global"
    } else {
        "$testTagPrefix-current-book"
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagPrefix),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { scopeMenuExpanded = true }
                .testTag(selectedTag)
                .semantics {
                    stateDescription = scopeLabel
                    contentDescription = "作用范围，$scopeLabel"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "作用范围",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                scopeLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                painter = painterResource(XinYueIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = scopeMenuExpanded,
            onDismissRequest = { scopeMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_scope_global)) },
                onClick = {
                    scopeMenuExpanded = false
                    onScopeChanged(ReaderSettingsScope.GLOBAL)
                },
                trailingIcon = if (scope == ReaderSettingsScope.GLOBAL) {
                    { Text("✓") }
                } else {
                    null
                },
                modifier = Modifier
                    .testTag("$testTagPrefix-global-option")
                    .semantics { selected = scope == ReaderSettingsScope.GLOBAL },
            )
            if (includeCurrentBook) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_scope_book)) },
                    onClick = {
                        scopeMenuExpanded = false
                        onScopeChanged(ReaderSettingsScope.CURRENT_BOOK)
                    },
                    trailingIcon = if (scope == ReaderSettingsScope.CURRENT_BOOK) {
                        { Text("✓") }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .testTag("$testTagPrefix-current-book-option")
                        .semantics { selected = scope == ReaderSettingsScope.CURRENT_BOOK },
                )
            }
        }
    }
}

@Composable
private fun QuickThemeChoice(
    theme: ReaderColorTheme,
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = theme.builtInPalette()
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag(tag)
            .semantics {
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(palette.backgroundArgb.toInt()))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Float.formatSp(): String =
    if (this % 1f == 0f) "${toInt()}" else "%.1f".format(Locale.ROOT, this)

private fun ReaderColorTheme.quickLabelResource(): Int = when (this) {
    ReaderColorTheme.PAPER -> R.string.reader_quick_paper
    ReaderColorTheme.SEPIA -> R.string.reader_quick_sepia
    ReaderColorTheme.GREEN -> R.string.reader_quick_green
    ReaderColorTheme.DARK -> R.string.reader_quick_dark
    ReaderColorTheme.OLED_BLACK -> R.string.reader_quick_black
}
