package com.xinyue.reader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import com.xinyue.reader.core.domain.model.ReaderThemePreset

@Composable
fun GlobalReaderSettingsRoute(
    section: GlobalReaderSettingsSection,
    onBack: () -> Unit,
    viewModel: GlobalReaderSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    GlobalReaderSettingsScreen(
        section = section,
        state = state,
        onBack = onBack,
        onPreview = viewModel::preview,
        onSave = { viewModel.save(section, onBack) },
        onCancelChanges = viewModel::cancel,
        onDismissError = viewModel::dismissError,
        onApplyTheme = viewModel::applyTheme,
        onCreateTheme = viewModel::createTheme,
        onCopyTheme = viewModel::copyTheme,
        onRenameTheme = viewModel::renameTheme,
        onUpdateTheme = viewModel::updateTheme,
        onDeleteTheme = viewModel::deleteTheme,
        onUpdateSchedule = viewModel::updateSchedule,
        onImportFont = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf")) },
        onRemoveFont = viewModel::removeFont,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalReaderSettingsScreen(
    section: GlobalReaderSettingsSection,
    state: GlobalReaderSettingsUiState,
    onBack: () -> Unit,
    onPreview: (ReaderSettings) -> Unit,
    onSave: () -> Unit,
    onCancelChanges: () -> Unit,
    onDismissError: () -> Unit,
    onApplyTheme: (String) -> Unit,
    onCreateTheme: (String) -> Unit,
    onCopyTheme: (String, String) -> Unit,
    onRenameTheme: (String, String) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onUpdateSchedule: (com.xinyue.reader.core.domain.model.ReaderThemeSchedule) -> Unit,
    onImportFont: () -> Unit,
    onRemoveFont: (String) -> Unit,
) {
    val editable = section in setOf(
        GlobalReaderSettingsSection.APPEARANCE,
        GlobalReaderSettingsSection.TYPOGRAPHY,
        GlobalReaderSettingsSection.BEHAVIOR,
        GlobalReaderSettingsSection.INFORMATION,
    )
    var confirmDiscard by remember { mutableStateOf(false) }
    fun requestBack() {
        if (editable && state.isDirty) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    Scaffold(
        modifier = Modifier.testTag("global_settings_${section.name.lowercase()}"),
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = { TextButton(onClick = ::requestBack) { Text("返回") } },
                actions = {
                    if (editable) {
                        TextButton(onClick = onSave, enabled = state.isDirty && !state.saving) { Text("保存") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.errorMessage?.let { message ->
                item {
                    Card(onClick = onDismissError, modifier = Modifier.fillMaxWidth()) {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (editable) {
                item { ReaderPreviewCard(state.draft) }
            }
            item {
                when (section) {
                    GlobalReaderSettingsSection.APPEARANCE -> ReaderTypographyControls(
                        settings = state.draft,
                        importedFonts = state.importedFonts,
                        advanced = false,
                        includeGlobalBehavior = true,
                        onPreview = onPreview,
                        onImportFont = onImportFont,
                        onRemoveImportedFont = onRemoveFont,
                        includeFontManagement = false,
                    )
                    GlobalReaderSettingsSection.TYPOGRAPHY -> ReaderTypographyControls(
                        settings = state.draft,
                        importedFonts = state.importedFonts,
                        advanced = true,
                        includeGlobalBehavior = false,
                        onPreview = onPreview,
                        onImportFont = onImportFont,
                        onRemoveImportedFont = onRemoveFont,
                    )
                    GlobalReaderSettingsSection.BEHAVIOR -> BehaviorControls(state.draft, onPreview)
                    GlobalReaderSettingsSection.INFORMATION -> InformationControls(state.draft, onPreview)
                    GlobalReaderSettingsSection.THEMES -> ThemeManagement(
                        state = state,
                        onApply = onApplyTheme,
                        onCreate = onCreateTheme,
                        onCopy = onCopyTheme,
                        onRename = onRenameTheme,
                        onUpdate = onUpdateTheme,
                        onDelete = onDeleteTheme,
                        onUpdateSchedule = onUpdateSchedule,
                    )
                    GlobalReaderSettingsSection.FONTS -> FontManagement(
                        state = state,
                        onImport = onImportFont,
                        onRemove = onRemoveFont,
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的更改？") },
            text = { Text("本页预览值尚未保存。") },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onCancelChanges()
                    onBack()
                }) { Text("放弃更改") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
        )
    }
}

@Composable
private fun ReaderPreviewCard(settings: ReaderSettings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("阅读预览", style = MaterialTheme.typography.labelMedium)
            Text(
                "山色入窗，翻开下一页。",
                fontSize = androidx.compose.ui.unit.TextUnit(settings.fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(settings.fontSizeSp * settings.lineHeightMultiplier, androidx.compose.ui.unit.TextUnitType.Sp),
            )
        }
    }
}

@Composable
private fun BehaviorControls(settings: ReaderSettings, onPreview: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("翻页动画", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderPageAnimation.entries.forEach { animation ->
                TextButton(onClick = { onPreview(settings.copy(pageAnimation = animation)) }) {
                    Text(if (settings.pageAnimation == animation) "✓ ${animation.label()}" else animation.label())
                }
            }
        }
        GlobalSwitch("阅读时保持亮屏", settings.keepScreenOn, testTag = "global_keep_screen") {
            onPreview(settings.copy(keepScreenOn = it))
        }
        GlobalSwitch("音量键翻页", settings.volumeKeyPageTurn) { onPreview(settings.copy(volumeKeyPageTurn = it)) }
        Text("九宫格点击", style = MaterialTheme.typography.titleMedium)
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val action = settings.tapZoneActions.getOrElse(index) { ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS[index] }
                    Button(
                        onClick = {
                            val actions = settings.tapZoneActions.toMutableList()
                            actions[index] = action.next()
                            onPreview(settings.copy(tapZoneActions = actions))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(action.label(), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun InformationControls(settings: ReaderSettings, onPreview: (ReaderSettings) -> Unit) {
    Column {
        GlobalSwitch("顶部显示书名", settings.showBookTitle) { onPreview(settings.copy(showBookTitle = it)) }
        GlobalSwitch("顶部显示章名", settings.showChapterTitle) { onPreview(settings.copy(showChapterTitle = it)) }
        GlobalSwitch("底部显示页码", settings.showPageNumber) { onPreview(settings.copy(showPageNumber = it)) }
        GlobalSwitch("底部显示全书进度", settings.showBookProgress) { onPreview(settings.copy(showBookProgress = it)) }
        GlobalSwitch("底部显示本章进度", settings.showChapterProgress) { onPreview(settings.copy(showChapterProgress = it)) }
        GlobalSwitch("底部显示时间", settings.showClock) { onPreview(settings.copy(showClock = it)) }
        GlobalSwitch("底部显示电量", settings.showBattery) { onPreview(settings.copy(showBattery = it)) }
    }
}

@Composable
private fun GlobalSwitch(
    label: String,
    checked: Boolean,
    testTag: String? = null,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = if (testTag == null) Modifier else Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun FontManagement(
    state: GlobalReaderSettingsUiState,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<com.xinyue.reader.core.domain.model.ImportedFont?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("字体文件会校验后复制到应用私有目录；原文件不会被修改。")
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("选择字体文件") }
        if (state.importedFonts.isEmpty()) Text("尚未导入字体", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.importedFonts.forEach { font ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(font.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${font.sizeBytes / 1024} KiB", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pendingRemoval = font }) { Text("删除") }
                }
            }
        }
    }
    pendingRemoval?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("删除字体？") },
            text = { Text("正在使用的字体不会被删除。") },
            confirmButton = { Button(onClick = { onRemove(font.id); pendingRemoval = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("取消") } },
        )
    }
}

private sealed interface ThemeDialog {
    data object Create : ThemeDialog
    data class Copy(val theme: ReaderThemePreset) : ThemeDialog
    data class Rename(val theme: ReaderThemePreset) : ThemeDialog
    data class Delete(val theme: ReaderThemePreset) : ThemeDialog
}

@Composable
private fun ThemeManagement(
    state: GlobalReaderSettingsUiState,
    onApply: (String) -> Unit,
    onCreate: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateSchedule: (com.xinyue.reader.core.domain.model.ReaderThemeSchedule) -> Unit,
) {
    var dialog by remember { mutableStateOf<ThemeDialog?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("主题", style = MaterialTheme.typography.titleMedium)
        state.themes.forEach { theme ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(theme.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onApply(theme.id) }) { Text("应用") }
                    }
                    FlowRow {
                        TextButton(onClick = { dialog = ThemeDialog.Copy(theme) }) { Text("复制") }
                        if (!theme.builtIn) {
                            TextButton(onClick = { dialog = ThemeDialog.Rename(theme) }) { Text("重命名") }
                            TextButton(onClick = { onUpdate(theme.id) }) { Text("更新为当前外观") }
                            TextButton(onClick = { dialog = ThemeDialog.Delete(theme) }) { Text("删除") }
                        }
                    }
                }
            }
        }
        Button(onClick = { dialog = ThemeDialog.Create }, modifier = Modifier.fillMaxWidth()) { Text("新建当前外观主题") }
        Text("自动切换", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ReaderThemeScheduleControls(
            schedule = state.schedule,
            themes = state.themes,
            manualOverride = state.manualOverride,
            onScheduleChanged = onUpdateSchedule,
        )
    }
    dialog?.let { request ->
        var name by remember(request) {
            mutableStateOf(
                when (request) {
                    is ThemeDialog.Copy -> "${request.theme.name} 副本"
                    is ThemeDialog.Rename -> request.theme.name
                    else -> ""
                },
            )
        }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (request is ThemeDialog.Delete) "删除主题？" else "主题名称") },
            text = {
                if (request is ThemeDialog.Delete) Text("删除后无法恢复，内置主题不会受影响。")
                else androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true)
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (request) {
                            ThemeDialog.Create -> onCreate(name)
                            is ThemeDialog.Copy -> onCopy(request.theme.id, name)
                            is ThemeDialog.Rename -> onRename(request.theme.id, name)
                            is ThemeDialog.Delete -> onDelete(request.theme.id)
                        }
                        dialog = null
                    },
                    enabled = request is ThemeDialog.Delete || name.isNotBlank(),
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
    }
}

private fun ReaderPageAnimation.label() = when (this) {
    ReaderPageAnimation.SLIDE -> "滑动"
    ReaderPageAnimation.COVER -> "覆盖"
    ReaderPageAnimation.NONE -> "无动画"
}

private fun ReaderTapAction.label() = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> "上一页"
    ReaderTapAction.NEXT_PAGE -> "下一页"
    ReaderTapAction.MENU -> "菜单"
    ReaderTapAction.NONE -> "无动作"
}

private fun ReaderTapAction.next() = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
    ReaderTapAction.NEXT_PAGE -> ReaderTapAction.MENU
    ReaderTapAction.MENU -> ReaderTapAction.NONE
    ReaderTapAction.NONE -> ReaderTapAction.PREVIOUS_PAGE
}
