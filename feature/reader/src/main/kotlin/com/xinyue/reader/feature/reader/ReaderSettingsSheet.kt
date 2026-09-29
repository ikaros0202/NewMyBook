package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule

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
    var advanced by remember { mutableStateOf(false) }
    var fullSettings by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf<ReaderThemeDialogRequest?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reader_appearance_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .then(if (fullSettings) Modifier.testTag("reader-settings-full") else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!fullSettings) {
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    ReaderQuickSettings(
                        settings = edit.preview,
                        modifier = Modifier.fillMaxWidth(),
                        scope = edit.scope,
                        onScopeChanged = onScopeChanged,
                        onSettingsChange = onPreview,
                        onMoreSettings = { fullSettings = true },
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScopeButton(stringResource(R.string.reader_scope_global), edit.scope == ReaderSettingsScope.GLOBAL) {
                            onScopeChanged(ReaderSettingsScope.GLOBAL)
                        }
                        if (bookTitle != null) {
                            ScopeButton(stringResource(R.string.reader_scope_book), edit.scope == ReaderSettingsScope.CURRENT_BOOK) {
                                onScopeChanged(ReaderSettingsScope.CURRENT_BOOK)
                            }
                        }
                    }
                    ScopeStatus(
                        edit = edit,
                        bookTitle = bookTitle,
                        bookOverrides = bookOverrides,
                        onClearCurrentBookOverrides = onClearCurrentBookOverrides,
                    )
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = { fullSettings = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("reader-settings-back-to-quick"),
                    ) { Text("返回快捷设置") }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScopeButton(
                            stringResource(R.string.reader_common),
                            !advanced,
                            testTag = "reader-settings-common",
                        ) { advanced = false }
                        ScopeButton(
                            stringResource(R.string.reader_advanced),
                            advanced,
                            testTag = "reader-settings-advanced",
                        ) { advanced = true }
                    }
                    ReaderTypographyControls(
                        settings = edit.preview,
                        importedFonts = importedFonts,
                        advanced = false,
                        includeGlobalBehavior = edit.scope == ReaderSettingsScope.GLOBAL,
                        onPreview = onPreview,
                        onImportFont = onImportFont,
                        onRemoveImportedFont = onRemoveImportedFont,
                    )

                    Text(stringResource(R.string.reader_themes_title), style = MaterialTheme.typography.titleMedium)
                    themes.forEach { theme ->
                        ThemeCard(
                            theme = theme,
                            active = theme.id == activeThemeId,
                            onApply = { onApplyTheme(theme.id) },
                            onCopy = { themeDialog = ReaderThemeDialogRequest.Copy(theme) },
                            onRename = { themeDialog = ReaderThemeDialogRequest.Rename(theme) },
                            onUpdate = { onUpdateTheme(theme.id) },
                            onDelete = { themeDialog = ReaderThemeDialogRequest.Delete(theme) },
                        )
                    }
                    OutlinedButton(
                        onClick = { themeDialog = ReaderThemeDialogRequest.Create },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("reader-theme-create"),
                    ) { Text(stringResource(R.string.reader_theme_create)) }

                    if (advanced) {
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
        },
        confirmButton = {
            Button(onClick = onCommit, enabled = edit.isDirty) {
                Text(stringResource(R.string.reader_save))
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

@Composable
private fun ScopeStatus(
    edit: ReaderAppearanceEdit,
    bookTitle: String?,
    bookOverrides: ReaderSettingsOverrides,
    onClearCurrentBookOverrides: () -> Unit,
) {
    if (edit.scope == ReaderSettingsScope.GLOBAL) {
        Text(stringResource(R.string.reader_scope_global_description))
        return
    }
    Text(stringResource(R.string.reader_scope_book_named, bookTitle.orEmpty()))
    Text(stringResource(R.string.reader_scope_book_description))
    val count = bookOverrides.nonNullFieldCount()
    Text(
        if (count == 0) {
            stringResource(R.string.reader_override_inherited)
        } else {
            stringResource(R.string.reader_override_count, count)
        },
    )
    Text(stringResource(R.string.reader_reset_book_explanation))
    TextButton(
        onClick = onClearCurrentBookOverrides,
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.reader_reset_book)) }
}

@Composable
private fun ThemeCard(
    theme: ReaderThemePreset,
    active: Boolean,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("reader-theme-${theme.id}"),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (active) 4.dp else 1.dp,
        border = if (active) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(readerThemePreviewColor(theme.settings.backgroundArgb), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("阅", color = readerThemePreviewColor(theme.settings.foregroundArgb))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(theme.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            active -> stringResource(R.string.reader_theme_active)
                            theme.builtIn -> stringResource(R.string.reader_theme_builtin)
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(
                    onClick = onApply,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reader-theme-apply-${theme.id}"),
                ) {
                    Text(stringResource(R.string.reader_theme_apply))
                }
            }
            if (theme.builtIn) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemeActionButton(
                        stringResource(R.string.reader_theme_copy),
                        onCopy,
                        testTag = "reader-theme-copy-${theme.id}",
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ThemeActionButton(
                        stringResource(R.string.reader_theme_copy),
                        onCopy,
                        testTag = "reader-theme-copy-${theme.id}",
                    )
                    ThemeActionButton(
                        stringResource(R.string.reader_theme_rename),
                        onRename,
                        testTag = "reader-theme-rename-${theme.id}",
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ThemeActionButton(
                        stringResource(R.string.reader_theme_update),
                        onUpdate,
                        testTag = "reader-theme-update-${theme.id}",
                    )
                    ThemeActionButton(
                        stringResource(R.string.reader_theme_delete),
                        onDelete,
                        testTag = "reader-theme-delete-${theme.id}",
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ThemeActionButton(
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun RowScopeButtonContent(label: String, selected: Boolean) {
    Text(if (selected) "✓ $label" else label)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ScopeButton(
    label: String,
    selected: Boolean,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f)
            .heightIn(min = 48.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
    ) { RowScopeButtonContent(label, selected) }
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
