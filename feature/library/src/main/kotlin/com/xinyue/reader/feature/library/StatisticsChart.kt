package com.xinyue.reader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val MAX_VISIBLE_BUCKETS = 7
private val CHART_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd")
private val CHART_HEIGHT = 184.dp
private val CHART_PLOT_HEIGHT = 116.dp
private val CHART_AXIS_WIDTH = 44.dp
private val CHART_BAR_MAX_HEIGHT = 76.dp
private val CHART_BAR_WIDTH = 24.dp
private val CHART_BAR_CORNER = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

internal data class StatisticsChartBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val activeMillis: Long,
    val sessionCount: Long,
) {
    val dateLabel: String
        get() = startDate.format(CHART_DATE_FORMATTER)
}

/**
 * Compresses long periods into a maximum of seven contiguous buckets so the chart keeps a
 * stable width on phones. The total duration and session count remain additive.
 */
internal fun statisticsChartBuckets(
    daily: List<DailyReadingStat>,
    maxBuckets: Int = MAX_VISIBLE_BUCKETS,
): List<StatisticsChartBucket> {
    if (daily.isEmpty()) return emptyList()
    val bucketCount = minOf(maxBuckets.coerceAtLeast(1), daily.size)
    return List(bucketCount) { bucketIndex ->
        val startIndex = bucketIndex * daily.size / bucketCount
        val endIndex = (bucketIndex + 1) * daily.size / bucketCount
        val items = daily.subList(startIndex, endIndex)
        StatisticsChartBucket(
            startDate = items.first().date,
            endDate = items.last().date,
            activeMillis = items.sumOf { it.activeMillis.coerceAtLeast(0L) },
            sessionCount = items.sumOf { it.sessionCount.coerceAtLeast(0L) },
        )
    }
}

@Composable
internal fun StatisticsChart(
    daily: List<DailyReadingStat>,
    modifier: Modifier = Modifier,
) {
    val buckets = statisticsChartBuckets(daily)
    val maximumValue = buckets.maxOfOrNull { it.activeMillis } ?: 0L
    val scaleMaximum = maximumValue.coerceAtLeast(1L)
    val chartDescription = if (buckets.isEmpty()) {
        "每日有效阅读时长图表，当前时段暂无阅读记录"
    } else {
        buildString {
            append("每日有效阅读时长图表，共 ")
            append(buckets.size)
            append(" 个数据桶。")
            buckets.forEach { bucket ->
                append(bucket.startDate)
                if (bucket.endDate != bucket.startDate) append(" 至 ").append(bucket.endDate)
                append("，")
                    .append(formatReadingDuration(bucket.activeMillis))
                    .append("，")
                    .append(bucket.sessionCount)
                    .append(" 次会话；")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = chartDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_PLOT_HEIGHT),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .width(CHART_AXIS_WIDTH)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = formatChartDuration(maximumValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = "0m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    buckets.forEach { bucket ->
                        ChartBucketBar(bucket, scaleMaximum)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = CHART_AXIS_WIDTH + 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            buckets.forEach { bucket ->
                ChartBucketDateLabel(bucket)
            }
        }
        Text(
            text = if (daily.size > buckets.size) {
                "柱高 = 有效阅读时长；长周期按连续日期合并"
            } else {
                "柱高 = 有效阅读时长"
            },
            modifier = Modifier.padding(start = CHART_AXIS_WIDTH + 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.ChartBucketBar(
    bucket: StatisticsChartBucket,
    maximum: Long,
) {
    val ratio = (bucket.activeMillis.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0)
    val barHeight = if (bucket.activeMillis <= 0L) {
        2.dp
    } else {
        (CHART_BAR_MAX_HEIGHT.value * ratio).roundToInt().coerceAtLeast(4).dp
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                contentDescription = buildString {
                    append(bucket.startDate)
                    if (bucket.endDate != bucket.startDate) {
                        append(" 至 ").append(bucket.endDate)
                    }
                    append("，")
                        .append(formatReadingDuration(bucket.activeMillis))
                        .append("，")
                        .append(bucket.sessionCount)
                        .append(" 次会话")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = formatChartDuration(bucket.activeMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(CHART_BAR_WIDTH)
                .height(barHeight)
                .background(MaterialTheme.colorScheme.primary, CHART_BAR_CORNER),
        )
    }
}

@Composable
private fun RowScope.ChartBucketDateLabel(bucket: StatisticsChartBucket) {
    Text(
        text = bucket.dateLabel,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

internal fun formatChartDuration(activeMillis: Long): String {
    if (activeMillis <= 0L) return "0m"
    if (activeMillis < 60_000L) return "<1m"
    val totalMinutes = activeMillis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h${minutes}m"
    }
}
