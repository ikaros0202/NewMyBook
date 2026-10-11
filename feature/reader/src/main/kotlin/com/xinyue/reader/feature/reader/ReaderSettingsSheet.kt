package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun ReaderSettingsSheet(
    edit: ReaderAppearanceEdit,
    importedFonts: List<ImportedFont>,
    themes: List<ReaderThemePreset>,
    activeThemeId: String?,
    themeSchedule: ReaderThemeSchedule,
    manualThemeOverride: ReaderThemeManualOverride?,
    bookTitle: String?,
    bookOverrides: ReaderSettingsOverrides,
    errorMessage: String?,
    onScopeChanged: (ReaderSettingsScope) -> Unit,
    onPreview: (ReaderSettings) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onClearCurrentBookOverrides: () -> Unit,
    onApplyTheme: (String) -> Unit,
    onCreateTheme: (String) -> Unit,
    onCopyTheme: (String, String) -> Unit,
    onRenameTheme: (String, String) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onUpdateThemeSchedule: (ReaderThemeSchedule) -> Unit,
    onImportFont: () -> Unit,
    onRemoveImportedFont: (String) -> Unit,
) {
    var fullSection by remember { mutableStateOf(ReaderFullSection.TYPOGRAPHY) }
    var fullSettings by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf<ReaderThemeDialogRequest?>(null) }
    // Each section owns a fresh scroll position. Switching tabs therefore always exposes the
    // beginning of that section, while returning to quick settings starts at its own top.
    val contentScrollState = remember(fullSettings, fullSection) { ScrollState(0) }
    ReaderAppearanceBottomSheet(
        expanded = fullSettings,
        onDismissRequest = { if (fullSettings) fullSettings = false else onCancel() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fullSettings) {
                    IconButton(
                        onClick = { fullSettings = false },
                        modifier = Modifier.testTag("reader-settings-back-to-quick"),
                    ) {
                        Icon(
                            painter = painterResource(XinYueIcons.ArrowBack),
                            contentDescription = "返回快捷设置",
                        )
                    }
                }
                Text(stringResource(R.string.reader_appearance_title))
            }
        },
        text = {
            if (!fullSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    ReaderQuickSettings(
                        settings = edit.preview,
                        modifier = Modifier.fillMaxWidth(),
                        scope = edit.scope,
                        onScopeChanged = onScopeChanged,
                        onSettingsChange = onPreview,
                        onMoreSettings = {
                            fullSection = ReaderFullSection.TYPOGRAPHY
                            fullSettings = true
                        },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .testTag("reader-settings-full"),
                ) {
                    // Scope and section navigation stay visible while the selected controls scroll.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ReaderScopeSelector(
                            scope = edit.scope,
                            onScopeChanged = onScopeChanged,
                            includeCurrentBook = bookTitle != null,
                            testTagPrefix = "reader-settings-scope",
                        )
                        ScopeStatus(
                            edit = edit,
                            bookTitle = bookTitle,
                            bookOverrides = bookOverrides,
                            onClearCurrentBookOverrides = onClearCurrentBookOverrides,
                        )
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FullSectionButton(
                                label = "排版",
                                selected = fullSection == ReaderFullSection.TYPOGRAPHY,
                                testTag = "reader-settings-common",
                            ) { fullSection = ReaderFullSection.TYPOGRAPHY }
                            FullSectionButton(
                                label = "主题",
                                selected = fullSection == ReaderFullSection.THEMES,
                                testTag = "reader-settings-themes",
                            ) { fullSection = ReaderFullSection.THEMES }
                            FullSectionButton(
                                label = "更多",
                                selected = fullSection == ReaderFullSection.MORE,
                                testTag = "reader-settings-advanced",
                            ) { fullSection = ReaderFullSection.MORE }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(contentScrollState),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (fullSection) {
                                ReaderFullSection.TYPOGRAPHY -> ReaderTypographyControls(
                                    settings = edit.preview,
                                    importedFonts = importedFonts,
                                    advanced = false,
                                    includeGlobalBehavior = edit.scope == ReaderSettingsScope.GLOBAL,
                                    onPreview = onPreview,
                                    onImportFont = onImportFont,
                                    onRemoveImportedFont = onRemoveImportedFont,
                                )
                                ReaderFullSection.THEMES -> {
                                    Text(stringResource(R.string.reader_themes_title), style = MaterialTheme.typography.titleMedium)
                                    themes.forEach { theme ->
                                        ReaderThemeRow(
                                            theme = theme,
                                            selected = theme.id == activeThemeId,
                                            onApply = { onApplyTheme(theme.id) },
                                            onCopy = { themeDialog = ReaderThemeDialogRequest.Copy(theme) },
                                            onRename = if (theme.builtIn) null else {
                                                { themeDialog = ReaderThemeDialogRequest.Rename(theme) }
                                            },
                                            onUpdate = if (theme.builtIn) null else { { onUpdateTheme(theme.id) } },
                                            onDelete = if (theme.builtIn) null else {
                                                { themeDialog = ReaderThemeDialogRequest.Delete(theme) }
                                            },
                                        )
                                    }
                                    TextButton(
                                        onClick = { themeDialog = ReaderThemeDialogRequest.Create },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("reader-theme-create"),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    ) { Text(stringResource(R.string.reader_theme_create)) }
                                }
                                ReaderFullSection.MORE -> {
                                    ReaderTypographyControls(
                                        settings = edit.preview,
                                        importedFonts = importedFonts,
                                        advanced = true,
                                        includeGlobalBehavior = edit.scope == ReaderSettingsScope.GLOBAL,
                                        onPreview = onPreview,
                                        onImportFont = onImportFont,
                                        onRemoveImportedFont = onRemoveImportedFont,
                                    )
                                    if (edit.scope == ReaderSettingsScope.GLOBAL) {
                                        ReaderThemeScheduleControls(
                                            schedule = themeSchedule,
                                            themes = themes,
                                            manualOverride = manualThemeOverride,
                                            onScheduleChanged = onUpdateThemeSchedule,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCommit, enabled = edit.isDirty, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reader_cancel))
            }
        },
    )
    ReaderThemeDialogHost(
        request = themeDialog,
        onDismiss = { themeDialog = null },
        onCreate = onCreateTheme,
        onCopy = onCopyTheme,
        onRename = onRenameTheme,
        onDelete = onDeleteTheme,
    )
}

/** A bounded, scrollable sheet keeps the current page visible while editing the same draft. */
@Composable
private fun ReaderAppearanceBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        KeepReaderDialogImmersive(dimAmount = 0f)
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.matchParentSize().clickable(onClick = onDismissRequest))
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * if (expanded) 0.92f else 0.72f)
                    .testTag("reader-appearance-sheet")
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                                title()
                            }
                        }
                        dismissButton()
                        confirmButton()
                    }
                    HorizontalDivider()
                    Box(Modifier.weight(1f, fill = expanded).fillMaxWidth()) { text() }
                }
            }
        }
    }
}

private enum class ReaderFullSection {
    TYPOGRAPHY,
    THEMES,
    MORE,
}

@Composable
private fun RowScope.FullSectionButton(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .testTag(testTag),
        color = Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                if (selected) {
                    Box(
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

@Composable
private fun ScopeStatus(
    edit: ReaderAppearanceEdit,
    bookTitle: String?,
    bookOverrides: ReaderSettingsOverrides,
    onClearCurrentBookOverrides: () -> Unit,
) {
    if (edit.scope == ReaderSettingsScope.GLOBAL) {
        Text(
            stringResource(R.string.reader_scope_global_description),
            modifier = Modifier.padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val count = bookOverrides.nonNullFieldCount()
    var detailsExpanded by remember(edit.scope, bookTitle, count) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (count == 0) {
                    "继承全局"
                } else {
                    "已自定义${count}项"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onClearCurrentBookOverrides,
                enabled = count > 0,
                modifier = Modifier.heightIn(min = 40.dp).testTag("reader-reset-book"),
            ) {
                Text(stringResource(R.string.reader_reset_book), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.heightIn(min = 40.dp).testTag("reader-scope-details"),
            ) {
                Text(if (detailsExpanded) "收起说明" else "说明", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (detailsExpanded) {
            Text(
                stringResource(R.string.reader_scope_book_named, bookTitle.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.reader_scope_book_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.reader_reset_book_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun ReaderSettingsOverrides.nonNullFieldCount(): Int = listOf(
    font,
    fontWeight,
    fontSizeSp,
    letterSpacingEm,
    lineHeightMultiplier,
    paragraphSpacingEm,
    firstLineIndentEm,
    alignment,
    horizontalPaddingDp,
    verticalPaddingDp,
    foregroundArgb,
    backgroundArgb,
    warmOverlayArgb,
    warmOverlayOpacity,
    focusBand,
).count { it != null }

/** Persisted reader colors are unsigned ARGB values stored in a Long, not Compose packed colors. */
internal fun readerThemePreviewColor(argb: Long): Color = Color(argb.toInt())
