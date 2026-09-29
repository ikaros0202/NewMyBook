package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import java.util.Locale

@Composable
internal fun ReaderTypographyControls(
    settings: ReaderSettings,
    importedFonts: List<ImportedFont>,
    advanced: Boolean,
    includeGlobalBehavior: Boolean,
    onPreview: (ReaderSettings) -> Unit,
    onImportFont: () -> Unit,
    onRemoveImportedFont: (String) -> Unit,
    includeFontManagement: Boolean = true,
) {
    if (advanced) {
        AdvancedTypographyControls(settings, includeGlobalBehavior, onPreview)
    } else {
        CommonTypographyControls(
            settings,
            importedFonts,
            includeGlobalBehavior,
            onPreview,
            onImportFont,
            onRemoveImportedFont,
            includeFontManagement,
        )
    }
}

@Composable
private fun CommonTypographyControls(
    settings: ReaderSettings,
    importedFonts: List<ImportedFont>,
    includeGlobalBehavior: Boolean,
    onPreview: (ReaderSettings) -> Unit,
    onImportFont: () -> Unit,
    onRemoveImportedFont: (String) -> Unit,
    includeFontManagement: Boolean,
) {
    Column(
        modifier = Modifier.testTag("reader-typography-common"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingTitle("字体")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ReaderFontRef.System to "系统",
                ReaderFontRef.Serif to "宋体",
                ReaderFontRef.SansSerif to "黑体",
            ).forEach { (font, label) ->
                ChoiceButton(label, settings.font == font) { onPreview(settings.copy(font = font)) }
            }
            importedFonts.forEach { font ->
                val ref = ReaderFontRef.Imported(font.id)
                ChoiceButton(font.displayName, settings.font == ref) { onPreview(settings.copy(font = ref)) }
            }
        }
        if (includeFontManagement) {
            Button(
                onClick = onImportFont,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("导入字体") }
            importedFonts.forEach { font ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(font.displayName, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onRemoveImportedFont(font.id) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("删除字体") }
                }
            }
        }
        ExactSlider(
            label = "字号",
            value = settings.fontSizeSp,
            valueText = "${format(settings.fontSizeSp, 1)} sp",
            range = 14f..36f,
            steps = 21,
            resetValue = 20f,
            onValueChange = { onPreview(settings.copy(fontSizeSp = it)) },
        )
        ExactSlider(
            label = "行间距",
            value = settings.lineHeightMultiplier,
            valueText = "${format(settings.lineHeightMultiplier, 2)} 倍",
            range = ReaderSettings.MIN_LINE_HEIGHT_MULTIPLIER..ReaderSettings.MAX_LINE_HEIGHT_MULTIPLIER,
            steps = 31,
            resetValue = 1.6f,
            onValueChange = { onPreview(settings.copy(lineHeightMultiplier = it)) },
        )
        ExactSlider(
            label = "段间距",
            value = settings.paragraphSpacingEm,
            valueText = "${format(settings.paragraphSpacingEm, 2)} em",
            range = ReaderSettings.MIN_PARAGRAPH_SPACING_EM..ReaderSettings.MAX_PARAGRAPH_SPACING_EM,
            steps = 19,
            resetValue = 0f,
            onValueChange = { onPreview(settings.copy(paragraphSpacingEm = it)) },
        )
        ExactSlider(
            label = "左右边距",
            value = settings.horizontalPaddingDp.toFloat(),
            valueText = "${settings.horizontalPaddingDp} dp",
            range = 8f..64f,
            steps = 13,
            resetValue = 24f,
            onValueChange = { onPreview(settings.copy(horizontalPaddingDp = it.toInt())) },
        )
        ExactSlider(
            label = "上下边距",
            value = settings.verticalPaddingDp.toFloat(),
            valueText = "${settings.verticalPaddingDp} dp",
            range = 8f..64f,
            steps = 13,
            resetValue = 16f,
            onValueChange = { onPreview(settings.copy(verticalPaddingDp = it.toInt())) },
        )
        SettingTitle("配色")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderColorTheme.entries.forEach { theme ->
                ChoiceButton(theme.displayName(), settings.matchesBuiltInPalette(theme)) {
                    onPreview(settings.withBuiltInPalette(theme))
                }
            }
        }
        if (includeGlobalBehavior) {
            SettingTitle("翻页方式")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderPageAnimation.entries.forEach { animation ->
                    ChoiceButton(animation.displayName(), settings.pageAnimation == animation) {
                        onPreview(settings.copy(pageAnimation = animation))
                    }
                }
            }
            Text(
                if (settings.brightness < 0f) "亮度：跟随系统" else "亮度：${(settings.brightness * 100).toInt()}%",
            )
            Slider(
                value = if (settings.brightness < 0f) 0.5f else settings.brightness,
                onValueChange = { onPreview(settings.copy(brightness = it)) },
                valueRange = 0.02f..1f,
                enabled = settings.brightness >= 0f,
                modifier = Modifier.semantics {
                    contentDescription = if (settings.brightness < 0f) {
                        "亮度，跟随系统"
                    } else {
                        "亮度，${(settings.brightness * 100).toInt()}%"
                    }
                },
            )
            TextButton(
                onClick = {
                    onPreview(settings.copy(brightness = if (settings.brightness < 0f) 0.5f else -1f))
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (settings.brightness < 0f) "使用独立亮度" else "恢复跟随系统") }
        }
    }
}

@Composable
private fun AdvancedTypographyControls(
    settings: ReaderSettings,
    includeGlobalBehavior: Boolean,
    onPreview: (ReaderSettings) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("reader-settings-full-advanced-controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExactSlider(
            label = "字重",
            value = settings.fontWeight.toFloat(),
            valueText = settings.fontWeight.toString(),
            range = 100f..900f,
            steps = 7,
            resetValue = 400f,
            onValueChange = { onPreview(settings.copy(fontWeight = it.toInt())) },
        )
        ExactSlider(
            label = "字距",
            value = settings.letterSpacingEm,
            valueText = "${format(settings.letterSpacingEm, 2)} em",
            range = -0.05f..0.20f,
            steps = 24,
            resetValue = 0f,
            onValueChange = { onPreview(settings.copy(letterSpacingEm = it)) },
        )
        ExactSlider(
            label = "首行缩进",
            value = settings.firstLineIndentEm,
            valueText = "${format(settings.firstLineIndentEm, 1)} em",
            range = 0f..4f,
            steps = 15,
            resetValue = 0f,
            onValueChange = { onPreview(settings.copy(firstLineIndentEm = it)) },
        )
        SettingTitle("对齐")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderTextAlignment.entries.forEach { alignment ->
                ChoiceButton(alignment.displayName(), settings.alignment == alignment) {
                    onPreview(settings.copy(alignment = alignment))
                }
            }
        }
        ReaderColorControls(settings = settings, onPreview = onPreview)
        AppearanceSwitchRow("专注阅读带", settings.focusBand.enabled, testTag = "reader-focus-band-toggle") {
            onPreview(settings.copy(focusBand = settings.focusBand.copy(enabled = it)))
        }
        if (settings.focusBand.enabled) {
            ReaderArgbEditor(
                label = "专注带颜色",
                argb = settings.focusBand.colorArgb,
                resetArgb = 0x22000000,
                onArgbChanged = { color ->
                    onPreview(settings.copy(focusBand = settings.focusBand.copy(colorArgb = color)))
                },
            )
            ExactSlider(
                label = "专注带行数",
                value = settings.focusBand.visibleLines.toFloat(),
                valueText = "${settings.focusBand.visibleLines} 行",
                range = 1f..8f,
                steps = 6,
                resetValue = 3f,
                onValueChange = {
                    onPreview(settings.copy(focusBand = settings.focusBand.copy(visibleLines = it.toInt())))
                },
            )
            ExactSlider(
                label = "专注带强度",
                value = settings.focusBand.opacity,
                valueText = "${(settings.focusBand.opacity * 100).toInt()}%",
                range = 0f..1f,
                steps = 99,
                resetValue = 0.14f,
                onValueChange = {
                    onPreview(settings.copy(focusBand = settings.focusBand.copy(opacity = it)))
                },
            )
            TextButton(
                onClick = {
                    onPreview(
                        settings.copy(focusBand = ReaderFocusBandSettings(enabled = true)),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("重置专注带") }
        }

        if (includeGlobalBehavior) {
            SettingTitle("阅读行为")
            AppearanceSwitchRow("阅读时保持亮屏", settings.keepScreenOn) {
                onPreview(settings.copy(keepScreenOn = it))
            }
            AppearanceSwitchRow("音量键翻页", settings.volumeKeyPageTurn) {
                onPreview(settings.copy(volumeKeyPageTurn = it))
            }
            SettingTitle("九宫格点击")
            repeat(3) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val action = settings.tapZoneActions.getOrElse(index) {
                            ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS[index]
                        }
                        Button(
                            onClick = {
                                val actions = settings.tapZoneActions
                                    .takeIf { it.size == ReaderSettings.TAP_ZONE_COUNT }
                                    ?.toMutableList()
                                    ?: ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS.toMutableList()
                                actions[index] = action.nextAction()
                                onPreview(settings.copy(tapZoneActions = actions))
                            },
                            modifier = Modifier.weight(1f)
                                .heightIn(min = 52.dp)
                                .semantics {
                                    contentDescription = "点击区域 ${index + 1}，${action.displayName()}"
                                },
                        ) { Text(action.displayName(), maxLines = 1) }
                    }
                }
            }
            SettingTitle("阅读信息")
            AppearanceSwitchRow("顶部显示书名", settings.showBookTitle) {
                onPreview(settings.copy(showBookTitle = it))
            }
            AppearanceSwitchRow("顶部显示章名", settings.showChapterTitle) {
                onPreview(settings.copy(showChapterTitle = it))
            }
            AppearanceSwitchRow("底部显示页码", settings.showPageNumber) {
                onPreview(settings.copy(showPageNumber = it))
            }
            AppearanceSwitchRow("底部显示全书进度", settings.showBookProgress) {
                onPreview(settings.copy(showBookProgress = it))
            }
            AppearanceSwitchRow("底部显示本章进度", settings.showChapterProgress) {
                onPreview(settings.copy(showChapterProgress = it))
            }
            AppearanceSwitchRow("底部显示时间", settings.showClock) {
                onPreview(settings.copy(showClock = it))
            }
            AppearanceSwitchRow("底部显示电量", settings.showBattery) {
                onPreview(settings.copy(showBattery = it))
            }
        }
    }
}

@Composable
private fun ExactSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    resetValue: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label：$valueText", modifier = Modifier.weight(1f))
        TextButton(onClick = { onValueChange(resetValue) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("重置")
        }
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        modifier = Modifier.semantics { contentDescription = "$label，$valueText" },
    )
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (selected) "✓ $label" else label)
    }
}

@Composable
private fun AppearanceSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label，${if (checked) "已开启" else "已关闭"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

internal fun parseArgbHex(value: String): Long? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 8 || normalized.any { it.digitToIntOrNull(16) == null }) return null
    return normalized.toULongOrNull(16)?.toLong()
}

private fun format(value: Float, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

private fun ReaderColorTheme.displayName(): String = when (this) {
    ReaderColorTheme.PAPER -> "纸白"
    ReaderColorTheme.SEPIA -> "米黄"
    ReaderColorTheme.GREEN -> "护眼"
    ReaderColorTheme.DARK -> "深灰"
    ReaderColorTheme.OLED_BLACK -> "OLED 黑"
}

private fun ReaderTextAlignment.displayName(): String = when (this) {
    ReaderTextAlignment.START -> "左对齐"
    ReaderTextAlignment.JUSTIFY -> "两端对齐"
    ReaderTextAlignment.CENTER -> "居中"
}

private fun ReaderPageAnimation.displayName(): String = when (this) {
    ReaderPageAnimation.SLIDE -> "滑动"
    ReaderPageAnimation.COVER -> "覆盖"
    ReaderPageAnimation.NONE -> "无动画"
}

private fun ReaderTapAction.displayName(): String = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> "上一页"
    ReaderTapAction.NEXT_PAGE -> "下一页"
    ReaderTapAction.MENU -> "菜单"
    ReaderTapAction.NONE -> "无动作"
}

private fun ReaderTapAction.nextAction(): ReaderTapAction = when (this) {
    ReaderTapAction.PREVIOUS_PAGE -> ReaderTapAction.NEXT_PAGE
    ReaderTapAction.NEXT_PAGE -> ReaderTapAction.MENU
    ReaderTapAction.MENU -> ReaderTapAction.NONE
    ReaderTapAction.NONE -> ReaderTapAction.PREVIOUS_PAGE
}
