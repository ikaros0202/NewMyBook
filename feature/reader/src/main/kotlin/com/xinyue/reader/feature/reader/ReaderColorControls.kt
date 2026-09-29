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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderColorContrast
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import java.util.Locale
import kotlin.math.roundToInt

internal data class NormalizedReaderColors(
    val foregroundArgb: Long,
    val backgroundArgb: Long,
    val contrastRatio: Double,
)

internal fun parseReaderArgb(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val parsed = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) 0xFF000000L or parsed else parsed
}

internal fun formatReaderArgb(argb: Long): String =
    String.format(Locale.ROOT, "#%08X", argb and 0xFFFFFFFFL)

internal fun normalizeReaderColors(foregroundArgb: Long, backgroundArgb: Long): NormalizedReaderColors {
    val result = ReaderColorContrast.evaluate(foregroundArgb, backgroundArgb)
    return NormalizedReaderColors(
        foregroundArgb = result.opaqueForegroundArgb,
        backgroundArgb = result.opaqueBackgroundArgb,
        contrastRatio = result.ratio,
    )
}

internal fun readerContrastWarning(foregroundArgb: Long, backgroundArgb: Long): String? =
    "对比度较低，长时间阅读可能疲劳".takeIf {
        ReaderColorContrast.evaluate(foregroundArgb, backgroundArgb).isLowContrast
    }

@Composable
internal fun ReaderColorControls(
    settings: ReaderSettings,
    onPreview: (ReaderSettings) -> Unit,
) {
    val normalized = normalizeReaderColors(settings.foregroundArgb, settings.backgroundArgb)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("自定义颜色", style = MaterialTheme.typography.titleSmall)
        ReaderArgbEditor(
            label = "文字颜色",
            testTag = "reader-foreground-argb",
            argb = settings.foregroundArgb,
            resetArgb = 0xFF2B2926,
            onArgbChanged = { foreground ->
                val colors = normalizeReaderColors(foreground, settings.backgroundArgb)
                onPreview(
                    settings.copy(
                        foregroundArgb = colors.foregroundArgb,
                        backgroundArgb = colors.backgroundArgb,
                    ),
                )
            },
        )
        ReaderArgbEditor(
            label = "背景颜色",
            testTag = "reader-background-argb",
            argb = settings.backgroundArgb,
            resetArgb = 0xFFF6F1E7,
            onArgbChanged = { background ->
                val colors = normalizeReaderColors(settings.foregroundArgb, background)
                onPreview(
                    settings.copy(
                        foregroundArgb = colors.foregroundArgb,
                        backgroundArgb = colors.backgroundArgb,
                    ),
                )
            },
        )
        Text("对比度 ${String.format(Locale.ROOT, "%.2f", normalized.contrastRatio)} : 1")
        readerContrastWarning(settings.foregroundArgb, settings.backgroundArgb)?.let { warning ->
            Text(warning, color = MaterialTheme.colorScheme.error)
        }
        ReaderArgbEditor(
            label = "暖色叠加颜色",
            argb = settings.warmOverlayArgb,
            resetArgb = 0xFFFFB35C,
            onArgbChanged = { onPreview(settings.copy(warmOverlayArgb = ReaderColorContrast.opaque(it))) },
        )
        ReaderChannelSlider(
            label = "暖色叠加强度",
            value = (settings.warmOverlayOpacity.coerceIn(0f, 1f) * 100).roundToInt(),
            range = 0..45,
            onValueChanged = { onPreview(settings.copy(warmOverlayOpacity = it / 100f)) },
            suffix = "%",
        )
    }
}

@Composable
internal fun ReaderArgbEditor(
    label: String,
    argb: Long,
    resetArgb: Long,
    testTag: String? = null,
    onArgbChanged: (Long) -> Unit,
) {
    var text by remember(argb) { mutableStateOf(formatReaderArgb(argb)) }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(Color(argb.toInt()), RoundedCornerShape(8.dp)),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { changed ->
                    text = changed.take(9)
                    parseReaderArgb(text)?.let(onArgbChanged)
                },
                modifier = Modifier
                    .weight(1f)
                    .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
                label = { Text(label) },
                supportingText = { if (parseReaderArgb(text) == null) Text("请输入 #RRGGBB 或 #AARRGGBB") },
                isError = parseReaderArgb(text) == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (expanded) "收起调色" else "展开调色")
            }
            TextButton(onClick = { onArgbChanged(resetArgb) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("重置")
            }
        }
        if (expanded) {
            listOf("红" to 16, "绿" to 8, "蓝" to 0).forEach { (channel, shift) ->
                val value = ((argb ushr shift) and 0xFF).toInt()
                ReaderChannelSlider(channel, value, 0..255, { next ->
                    val mask = 0xFFL shl shift
                    onArgbChanged((argb and mask.inv()) or (next.toLong() shl shift))
                })
            }
        }
    }
}

@Composable
private fun ReaderChannelSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
    suffix: String = "",
) {
    Column {
        Text("$label $value$suffix")
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
internal fun ReaderThemeScheduleControls(
    schedule: ReaderThemeSchedule,
    themes: List<ReaderThemePreset>,
    manualOverride: ReaderThemeManualOverride?,
    onScheduleChanged: (ReaderThemeSchedule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("自动主题", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeScheduleMode.entries.forEach { mode ->
                FilterChip(
                    selected = schedule.mode == mode,
                    onClick = { onScheduleChanged(schedule.copy(mode = mode)) },
                    modifier = Modifier.testTag(
                        "reader-theme-schedule-mode-${mode.name.lowercase(Locale.ROOT)}",
                    ),
                    label = {
                        Text(
                            when (mode) {
                                ThemeScheduleMode.OFF -> "关闭"
                                ThemeScheduleMode.FOLLOW_SYSTEM -> "跟随系统"
                                ThemeScheduleMode.FIXED_TIME -> "固定时段"
                            },
                        )
                    },
                )
            }
        }
        if (schedule.mode != ThemeScheduleMode.OFF) {
            ThemeChoiceRow("日间主题", "light", schedule.lightThemeId, themes) {
                onScheduleChanged(schedule.copy(lightThemeId = it))
            }
            ThemeChoiceRow("夜间主题", "dark", schedule.darkThemeId, themes) {
                onScheduleChanged(schedule.copy(darkThemeId = it))
            }
            if (schedule.mode == ThemeScheduleMode.FIXED_TIME) {
                MinuteOfDaySlider("日间开始", schedule.lightMinuteOfDay) {
                    onScheduleChanged(schedule.copy(lightMinuteOfDay = it))
                }
                MinuteOfDaySlider("夜间开始", schedule.darkMinuteOfDay) {
                    onScheduleChanged(schedule.copy(darkMinuteOfDay = it))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("手动主题保持到下一次切换")
                    Text("关闭后，自动规则会立即接管", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = schedule.manualOverrideUntilNextSwitch,
                    onCheckedChange = {
                        onScheduleChanged(schedule.copy(manualOverrideUntilNextSwitch = it))
                    },
                    modifier = Modifier.testTag("reader-theme-schedule-manual-override"),
                )
            }
            manualOverride?.let { Text("当前手动主题将在下一次自动边界恢复") }
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    label: String,
    slot: String,
    selectedId: String,
    themes: List<ReaderThemePreset>,
    onSelected: (String) -> Unit,
) {
    Text(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        themes.forEach { theme ->
            FilterChip(
                selected = theme.id == selectedId,
                onClick = { onSelected(theme.id) },
                modifier = Modifier.testTag("reader-theme-schedule-$slot-${theme.id}"),
                label = { Text(theme.name) },
            )
        }
    }
}

@Composable
private fun MinuteOfDaySlider(label: String, minuteOfDay: Int, onChanged: (Int) -> Unit) {
    val safe = minuteOfDay.coerceIn(0, 1439)
    var preview by remember(safe) { mutableStateOf(safe) }
    val formatted = String.format(Locale.ROOT, "%02d:%02d", preview / 60, preview % 60)
    Text("$label $formatted")
    Slider(
        value = preview.toFloat(),
        onValueChange = { preview = it.roundToInt().coerceIn(0, 1439) },
        onValueChangeFinished = { onChanged(preview) },
        valueRange = 0f..1439f,
        steps = 1438,
    )
}
