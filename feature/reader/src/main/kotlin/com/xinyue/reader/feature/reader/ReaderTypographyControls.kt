package com.xinyue.reader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "字体",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (includeFontManagement) {
                TextButton(
                    onClick = onImportFont,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text("导入字体") }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(
                ReaderFontRef.System to "系统",
                ReaderFontRef.Serif to "宋体",
                ReaderFontRef.SansSerif to "黑体",
            ).forEach { (font, label) ->
                ReaderChoiceOption(label, settings.font == font) { onPreview(settings.copy(font = font)) }
            }
            importedFonts.forEach { font ->
                val ref = ReaderFontRef.Imported(font.id)
                ReaderChoiceOption(font.displayName, settings.font == ref) { onPreview(settings.copy(font = ref)) }
            }
        }
        if (includeFontManagement) {
            importedFonts.forEach { font ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(font.displayName, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onRemoveImportedFont(font.id) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { Text("删除字体") }
                }
            }
        }
        ReaderNumericStepper(
            id = "reader-font-size",
            label = "字号",
            value = settings.fontSizeSp,
            valueText = formatNumber(settings.fontSizeSp, 0),
            range = 14f..36f,
            step = 1f,
            resetValue = 20f,
            decimals = 0,
            onValueChange = { onPreview(settings.copy(fontSizeSp = it)) },
        )
        ReaderNumericStepper(
            id = "reader-line-height",
            label = "行间距",
            value = settings.lineHeightMultiplier,
            valueText = "${formatNumber(settings.lineHeightMultiplier, 4)} 倍",
            range = ReaderSettings.MIN_LINE_HEIGHT_MULTIPLIER..ReaderSettings.MAX_LINE_HEIGHT_MULTIPLIER,
            step = 0.0375f,
            resetValue = 1.6f,
            decimals = 4,
            onValueChange = { onPreview(settings.copy(lineHeightMultiplier = it)) },
        )
        ReaderNumericStepper(
            id = "reader-paragraph-spacing",
            label = "段间距",
            value = settings.paragraphSpacingEm,
            valueText = "${formatNumber(settings.paragraphSpacingEm, 2)} 字",
            range = ReaderSettings.MIN_PARAGRAPH_SPACING_EM..ReaderSettings.MAX_PARAGRAPH_SPACING_EM,
            step = 0.05f,
            resetValue = 0f,
            decimals = 2,
            onValueChange = { onPreview(settings.copy(paragraphSpacingEm = it)) },
        )
        ReaderNumericStepper(
            id = "reader-horizontal-padding",
            label = "左右边距",
            value = settings.horizontalPaddingDp.toFloat(),
            valueText = settings.horizontalPaddingDp.toString(),
            range = 8f..64f,
            step = 4f,
            resetValue = 24f,
            decimals = 0,
            onValueChange = { onPreview(settings.copy(horizontalPaddingDp = it.roundToInt())) },
        )
        ReaderNumericStepper(
            id = "reader-vertical-padding",
            label = "上下边距",
            value = settings.verticalPaddingDp.toFloat(),
            valueText = settings.verticalPaddingDp.toString(),
            range = 8f..64f,
            step = 4f,
            resetValue = 16f,
            decimals = 0,
            onValueChange = { onPreview(settings.copy(verticalPaddingDp = it.roundToInt())) },
        )
        SettingTitle("配色")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ReaderColorTheme.entries.forEach { theme ->
                ReaderChoiceOption(theme.displayName(), settings.matchesBuiltInPalette(theme)) {
                    onPreview(settings.withBuiltInPalette(theme))
                }
            }
        }
        if (includeGlobalBehavior) {
            SettingTitle("翻页方式")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ReaderPageAnimation.entries.forEach { animation ->
                    ReaderChoiceOption(animation.displayName(), settings.pageAnimation == animation) {
                        onPreview(settings.copy(pageAnimation = animation))
                    }
                }
            }
            ReaderBrightnessControl(settings = settings, onSettingsChange = onPreview)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingTitle("文字细节")
        ReaderNumericStepper(
            id = "reader-font-weight",
            label = "字重",
            value = settings.fontWeight.toFloat(),
            valueText = settings.fontWeight.toString(),
            range = 100f..900f,
            step = 100f,
            resetValue = 400f,
            decimals = 0,
            onValueChange = { onPreview(settings.copy(fontWeight = it.roundToInt())) },
        )
        ReaderNumericStepper(
            id = "reader-letter-spacing",
            label = "字距",
            value = settings.letterSpacingEm,
            valueText = "${formatNumber(settings.letterSpacingEm, 2)} 字",
            range = -0.05f..0.20f,
            step = 0.01f,
            resetValue = 0f,
            decimals = 2,
            onValueChange = { onPreview(settings.copy(letterSpacingEm = it)) },
        )
        ReaderNumericStepper(
            id = "reader-first-line-indent",
            label = "首行缩进",
            value = settings.firstLineIndentEm,
            valueText = "${formatNumber(settings.firstLineIndentEm, 2)} 字",
            range = 0f..4f,
            step = 0.25f,
            resetValue = 0f,
            decimals = 2,
            onValueChange = { onPreview(settings.copy(firstLineIndentEm = it)) },
        )
        SettingTitle("对齐")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ReaderTextAlignment.entries.forEach { alignment ->
                ReaderChoiceOption(alignment.displayName(), settings.alignment == alignment) {
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
            ReaderNumericStepper(
                id = "reader-focus-lines",
                label = "专注带行数",
                value = settings.focusBand.visibleLines.toFloat(),
                valueText = "${settings.focusBand.visibleLines} 行",
                range = 1f..8f,
                step = 1f,
                resetValue = 3f,
                decimals = 0,
                onValueChange = {
                    onPreview(
                        settings.copy(
                            focusBand = settings.focusBand.copy(visibleLines = it.roundToInt()),
                        ),
                    )
                },
            )
            ReaderNumericStepper(
                id = "reader-focus-opacity",
                label = "专注带强度",
                value = settings.focusBand.opacity,
                valueText = "${formatNumber(settings.focusBand.opacity * 100f, 0)}%",
                range = 0f..1f,
                step = 0.01f,
                resetValue = ReaderFocusBandSettings.DEFAULT_OPACITY,
                decimals = 2,
                onValueChange = {
                    onPreview(settings.copy(focusBand = settings.focusBand.copy(opacity = it)))
                },
            )
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
            Text(
                text = stringResource(R.string.reader_tap_mapping_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReaderTapZoneGrid(
                actions = settings.tapZoneActions,
                onActionsChange = { onPreview(settings.copy(tapZoneActions = it)) },
            )
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
private fun ReaderNumericStepper(
    id: String,
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    resetValue: Float?,
    decimals: Int,
    onValueChange: (Float) -> Unit,
) {
    var editorVisible by remember(id) { mutableStateOf(false) }
    var draft by remember(id, value) { mutableStateOf(formatNumber(value, decimals)) }
    // A persisted value can predate the slider's discrete lattice (the default line height is
    // 1.6 while its old 31-step slider advances by 0.0375). The buttons must still move by
    // exactly one advertised step instead of first snapping and producing a larger jump.
    val decrease = (value - step).coerceIn(range)
    val increase = (value + step).coerceIn(range)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(id),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = { onValueChange(decrease) },
            enabled = value > range.start,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("$id-decrease")
                .semantics { contentDescription = "减少$label" },
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        TextButton(
            onClick = { editorVisible = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("$id-value")
                .semantics { contentDescription = "$label，$valueText，点击编辑" },
        ) { Text(valueText, style = MaterialTheme.typography.labelLarge) }
        TextButton(
            onClick = { onValueChange(increase) },
            enabled = value < range.endInclusive,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("$id-increase")
                .semantics { contentDescription = "增加$label" },
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
    if (editorVisible) {
        val parsed = draft.toFloatOrNull()?.takeIf { it.isFinite() }
        AlertDialog(
            onDismissRequest = { editorVisible = false },
            title = { Text("调整$label") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(valueText) },
                    supportingText = {
                        Text(
                            "范围 ${formatNumber(range.start, decimals)}–${formatNumber(range.endInclusive, decimals)}，步长 ${formatNumber(step, decimals)}",
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        // Decimal keyboards often omit the minus key; use a text keyboard for
                        // the letter-spacing control so its negative range remains editable.
                        keyboardType = if (range.start < 0f) KeyboardType.Text else KeyboardType.Decimal,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let {
                            onValueChange(
                                if (abs(it - value) < 0.00001f) value else snapNumericValue(it, range, step),
                            )
                            editorVisible = false
                        }
                    },
                    enabled = parsed != null,
                ) { Text("确定") }
            },
            dismissButton = {
                Row {
                    resetValue?.let { reset ->
                        TextButton(onClick = {
                            onValueChange(reset.coerceIn(range))
                            editorVisible = false
                        }) { Text("恢复默认") }
                    }
                    TextButton(onClick = { editorVisible = false }) { Text("取消") }
                }
            },
        )
    }
}

private fun snapNumericValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    val bounded = value.coerceIn(range)
    val intervals = ((bounded - range.start) / step).roundToInt()
    return (range.start + intervals * step).coerceIn(range)
}

@Composable
internal fun ReaderChoiceOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Box(
            modifier = Modifier
                .widthIn(min = 24.dp)
                .heightIn(min = 2.dp)
                .padding(top = 3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
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
        modifier = Modifier
            .fillMaxWidth()
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
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}

internal fun parseArgbHex(value: String): Long? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 8 || normalized.any { it.digitToIntOrNull(16) == null }) return null
    return normalized.toULongOrNull(16)?.toLong()
}

internal fun formatNumber(value: Float, decimals: Int): String {
    if (decimals == 0) return String.format(Locale.ROOT, "%.0f", value)
    val fixed = String.format(Locale.ROOT, "%.${decimals}f", value)
    return fixed.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

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
