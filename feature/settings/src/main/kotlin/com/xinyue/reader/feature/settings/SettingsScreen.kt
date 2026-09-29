package com.xinyue.reader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.ui.XinYueIcons

enum class SettingsSection(val title: String) {
    COMMON_READING("常用阅读"),
    PERSONALIZATION("个性化"),
    INFORMATION_LOCAL("信息与本地数据"),
}

enum class SettingsEntry(
    val title: String,
    val summary: String,
    val section: SettingsSection,
) {
    APPEARANCE("全局阅读外观", "字体、字号、行高、边距、配色与亮度", SettingsSection.COMMON_READING),
    THEMES("主题与自动切换", "主题管理、跟随系统与固定时段", SettingsSection.PERSONALIZATION),
    FONTS("导入字体", "导入、查看与删除应用私有字体", SettingsSection.PERSONALIZATION),
    TYPOGRAPHY("精细排版与专注带", "字重、字距、段落、自定义颜色与专注带", SettingsSection.PERSONALIZATION),
    BEHAVIOR("阅读行为", "翻页动画、保持亮屏、音量键与点击区域", SettingsSection.COMMON_READING),
    INFORMATION("阅读信息", "书名、章名、页码、进度、时间与电量", SettingsSection.INFORMATION_LOCAL),
    BACKUP("备份与恢复", "本地导出、检查、预览与恢复", SettingsSection.INFORMATION_LOCAL),
}

private val SettingsEntry.iconRes: Int
    get() = when (this) {
        SettingsEntry.APPEARANCE -> XinYueIcons.Appearance
        SettingsEntry.THEMES -> XinYueIcons.Themes
        SettingsEntry.FONTS -> XinYueIcons.Fonts
        SettingsEntry.TYPOGRAPHY -> XinYueIcons.Typography
        SettingsEntry.BEHAVIOR -> XinYueIcons.Behavior
        SettingsEntry.INFORMATION -> XinYueIcons.Information
        SettingsEntry.BACKUP -> XinYueIcons.Backup
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenEntry: (SettingsEntry) -> Unit) {
    Scaffold(
        modifier = Modifier.testTag("settings_root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(SettingsSection.entries, key = SettingsSection::name) { section ->
                SettingsGroup(
                    section = section,
                    entries = SettingsEntry.entries.filter { it.section == section },
                    onOpenEntry = onOpenEntry,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    section: SettingsSection,
    entries: List<SettingsEntry>,
    onOpenEntry: (SettingsEntry) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("settings_section_${section.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                SettingsRow(entry = entry, onClick = { onOpenEntry(entry) })
                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 36.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_${entry.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(XinYueIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
