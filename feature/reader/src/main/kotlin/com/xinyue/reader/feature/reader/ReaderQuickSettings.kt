package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import java.util.Locale

/**
 * The high-frequency reader settings surface. It only renders a draft and emits changes;
 * persistence and navigation remain owned by the caller.
 */
@Composable
internal fun ReaderQuickSettings(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    scope: ReaderSettingsScope,
    onScopeChanged: (ReaderSettingsScope) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onMoreSettings: () -> Unit,
) {
    val safeSettings = settings.normalized()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("reader-quick-settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickSection("reader-quick-settings-scope", "作用范围") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickChoice(
                    label = "全局默认",
                    selected = scope == ReaderSettingsScope.GLOBAL,
                    tag = "reader-quick-settings-scope-global",
                    onClick = { onScopeChanged(ReaderSettingsScope.GLOBAL) },
                )
                QuickChoice(
                    label = "当前书籍",
                    selected = scope == ReaderSettingsScope.CURRENT_BOOK,
                    tag = "reader-quick-settings-scope-current-book",
                    onClick = { onScopeChanged(ReaderSettingsScope.CURRENT_BOOK) },
                )
                if (scope == ReaderSettingsScope.CURRENT_BOOK) {
                    Text(
                        text = "翻页方式与亮度沿用全局默认",
                        modifier = Modifier.testTag("reader-quick-settings-global-only-note"),
                    )
                }
            }
        }

        QuickSlider(
            sectionTag = "reader-quick-settings-font-size",
            sliderTag = "reader-quick-settings-font-size-slider",
            label = "字号",
            value = safeSettings.fontSizeSp,
            valueText = "${formatQuick(safeSettings.fontSizeSp, 1)} sp",
            range = 14f..36f,
            steps = 21,
            onValueChange = { onSettingsChange(safeSettings.copy(fontSizeSp = it)) },
        )

        QuickSlider(
            sectionTag = "reader-quick-settings-line-height",
            sliderTag = "reader-quick-settings-line-height-slider",
            label = "行高",
            value = safeSettings.lineHeightMultiplier,
            valueText = "${formatQuick(safeSettings.lineHeightMultiplier, 2)} 倍",
            range = ReaderSettings.MIN_LINE_HEIGHT_MULTIPLIER..ReaderSettings.MAX_LINE_HEIGHT_MULTIPLIER,
            steps = 23,
            onValueChange = { onSettingsChange(safeSettings.copy(lineHeightMultiplier = it)) },
        )

        QuickSlider(
            sectionTag = "reader-quick-settings-paragraph-spacing",
            sliderTag = "reader-quick-settings-paragraph-spacing-slider",
            label = "段间距",
            value = safeSettings.paragraphSpacingEm,
            valueText = "${formatQuick(safeSettings.paragraphSpacingEm, 2)} em",
            range = ReaderSettings.MIN_PARAGRAPH_SPACING_EM..ReaderSettings.MAX_PARAGRAPH_SPACING_EM,
            steps = 19,
            onValueChange = { onSettingsChange(safeSettings.copy(paragraphSpacingEm = it)) },
        )

        QuickSection("reader-quick-settings-indent", "首行缩进") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0f, 1f, 2f).forEach { indent ->
                    QuickChoice(
                        label = "${formatQuick(indent, 0)}em",
                        selected = safeSettings.firstLineIndentEm == indent,
                        tag = "reader-quick-settings-indent-${indent.toInt()}",
                        onClick = { onSettingsChange(safeSettings.copy(firstLineIndentEm = indent)) },
                    )
                }
            }
        }

        if (scope == ReaderSettingsScope.GLOBAL) {
            QuickSection("reader-quick-settings-page-method", "翻页方式") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderPageAnimation.entries.forEach { animation ->
                        QuickChoice(
                            label = animation.quickLabel(),
                            selected = safeSettings.pageAnimation == animation,
                            tag = "reader-quick-settings-page-${animation.name.lowercase(Locale.ROOT)}",
                            onClick = { onSettingsChange(safeSettings.copy(pageAnimation = animation)) },
                        )
                    }
                }
            }
        }

        QuickSection("reader-quick-settings-font", "字体") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ReaderFontRef.System to ("系统" to "system"),
                    ReaderFontRef.Serif to ("宋体" to "serif"),
                    ReaderFontRef.SansSerif to ("黑体" to "sans-serif"),
                ).forEach { (font, labelAndTag) ->
                    val (label, tagName) = labelAndTag
                    QuickChoice(
                        label = label,
                        selected = safeSettings.font == font,
                        tag = "reader-quick-settings-font-$tagName",
                        onClick = { onSettingsChange(safeSettings.copy(font = font)) },
                    )
                }
            }
        }

        QuickSection("reader-quick-settings-theme-brightness", "主题与亮度") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderColorTheme.entries.forEach { theme ->
                    QuickChoice(
                        label = theme.quickLabel(),
                        selected = safeSettings.matchesBuiltInPalette(theme),
                        tag = "reader-quick-settings-theme-${theme.name.lowercase(Locale.ROOT)}",
                        onClick = { onSettingsChange(safeSettings.withBuiltInPalette(theme)) },
                    )
                }
            }
            if (scope == ReaderSettingsScope.GLOBAL) {
                val brightness = safeSettings.brightness
                Text(
                    if (brightness < 0f) {
                        "亮度：跟随系统"
                    } else {
                        "亮度：${(brightness * 100).toInt()}%"
                    },
                )
                QuickSliderTarget(
                    value = if (brightness < 0f) 0.5f else brightness,
                    onValueChange = { onSettingsChange(safeSettings.copy(brightness = it)) },
                    valueRange = 0.02f..1f,
                    enabled = brightness >= 0f,
                    tag = "reader-quick-settings-brightness-slider",
                    description = "亮度",
                )
                TextButton(
                    onClick = {
                        onSettingsChange(safeSettings.copy(brightness = if (brightness < 0f) 0.5f else -1f))
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reader-quick-settings-brightness-toggle"),
                ) {
                    Text(if (brightness < 0f) "使用独立亮度" else "恢复跟随系统")
                }
            }
        }

        QuickSection("reader-quick-settings-more-section", "") {
            OutlinedButton(
                onClick = onMoreSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reader-quick-settings-more"),
            ) {
                Text("更多设置")
            }
        }
    }
}

@Composable
private fun QuickSection(
    tag: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = {
            if (title.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        },
    )
}

@Composable
private fun QuickSlider(
    sectionTag: String,
    sliderTag: String,
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    QuickSection(sectionTag, "") {
        Text("$label：$valueText")
        QuickSliderTarget(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            tag = sliderTag,
            description = "$label：$valueText",
        )
    }
}

@Composable
private fun QuickSliderTarget(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    tag: String,
    description: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(tag)
                .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun QuickChoice(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = label
                stateDescription = if (selected) "已选择" else "未选择"
            },
    )
}

private fun ReaderPageAnimation.quickLabel(): String = when (this) {
    ReaderPageAnimation.SLIDE -> "滑动"
    ReaderPageAnimation.COVER -> "覆盖"
    ReaderPageAnimation.NONE -> "无动画"
}

private fun ReaderColorTheme.quickLabel(): String = when (this) {
    ReaderColorTheme.PAPER -> "纸张"
    ReaderColorTheme.SEPIA -> "棕褐"
    ReaderColorTheme.GREEN -> "护眼"
    ReaderColorTheme.DARK -> "深灰"
    ReaderColorTheme.OLED_BLACK -> "纯黑"
}

private fun formatQuick(value: Float, decimals: Int): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)
