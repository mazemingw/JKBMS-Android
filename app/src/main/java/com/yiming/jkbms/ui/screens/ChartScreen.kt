package com.yiming.jkbms.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yiming.jkbms.model.ChartMetricSource
import com.yiming.jkbms.model.TrendSample
import com.yiming.jkbms.ui.components.EChartPoint
import com.yiming.jkbms.ui.components.EChartsLine
import java.util.Locale

@Composable
fun ChartPage(
    modifier: Modifier = Modifier,
    chartSource: ChartMetricSource,
    secondChartSource: ChartMetricSource,
    secondChartEnabled: Boolean,
    recording: Boolean,
    playback: Boolean,
    samples: List<TrendSample>,
    onSourceChange: (ChartMetricSource) -> Unit,
    onSecondSourceChange: (ChartMetricSource) -> Unit,
    onSecondChartEnabledChange: (Boolean) -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    // Playback is a stable snapshot. Collecting live data continues independently.
    val playbackSamples = remember(playback) { samples }
    val displaySamples = remember(samples, playback, playbackSamples) {
        if (playback) playbackSamples else {
            val latestTs = samples.lastOrNull()?.timestampMs ?: 0L
            samples.filter { it.timestampMs >= latestTs - CHART_WINDOW_MS }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    if (playback) "全程回读 · ${displaySamples.size} 个样本" else "实时窗口 60 秒 · 已录制 ${samples.size} 个样本",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onToggleRecord) { Text(if (recording) "暂停录制" else "继续录制") }
                    TextButton(onClick = onTogglePlayback) { Text(if (playback) "返回实时" else "回读全程") }
                    TextButton(onClick = { onSecondChartEnabledChange(!secondChartEnabled) }) {
                        Text(if (secondChartEnabled) "切换单图" else "切换双图")
                    }
                }
                Text(
                    if (recording) "自动录制中；回读不影响采集" else "录制已暂停，点击继续录制恢复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (secondChartEnabled && maxWidth >= 700.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChart("图表 1", chartSource, displaySamples, onSourceChange, Modifier.weight(1f))
                    MetricChart("图表 2", secondChartSource, displaySamples, onSecondSourceChange, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChart("图表 1", chartSource, displaySamples, onSourceChange, compact = secondChartEnabled)
                    if (secondChartEnabled) {
                        MetricChart("图表 2", secondChartSource, displaySamples, onSecondSourceChange, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChart(
    title: String,
    source: ChartMetricSource,
    samples: List<TrendSample>,
    onSourceChange: (ChartMetricSource) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    val points = remember(samples, source) { samples.map { EChartPoint(it.timestampMs, valueFromSource(it, source)) } }
    val values = remember(points) { points.mapNotNull { it.value?.takeIf { value -> value.isFinite() } } }
    val color = sourceColor(source)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(6.dp)) {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("$title · ${source.label} (${source.unit}) ▾") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ChartMetricSource.entries.forEach { metric ->
                        DropdownMenuItem(
                            text = { Text("${metric.label} (${metric.unit})") },
                            onClick = { onSourceChange(metric); menuOpen = false }
                        )
                    }
                }
            }
            Text(
                "MAX ${fmt(values.maxOrNull())}  MIN ${fmt(values.minOrNull())} ${source.unit}",
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Box(Modifier.fillMaxWidth().height(if (compact) 170.dp else 290.dp), contentAlignment = Alignment.Center) {
                if (values.size < 2) {
                    Text(
                        if (source == ChartMetricSource.SPEED) "开启 GPS 后自动记录有效速度" else "连接 BMS 后自动录制曲线",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    EChartsLine(
                        points = points,
                        name = source.label,
                        unit = source.unit,
                        color = color,
                        modifier = Modifier.fillMaxSize(),
                        decimals = if (source == ChartMetricSource.SOC || source == ChartMetricSource.SPEED) 1 else 2
                    )
                }
            }
        }
    }
}

private fun valueFromSource(sample: TrendSample, source: ChartMetricSource): Float? = when (source) {
    ChartMetricSource.VOLTAGE -> sample.voltage
    ChartMetricSource.CURRENT -> sample.current
    ChartMetricSource.POWER -> sample.power
    ChartMetricSource.SOC -> sample.soc
    ChartMetricSource.SPEED -> sample.speedKmh
}

private fun sourceColor(source: ChartMetricSource): Color = when (source) {
    ChartMetricSource.VOLTAGE -> Color(0xFF42A5F5)
    ChartMetricSource.CURRENT -> Color(0xFF26C6DA)
    ChartMetricSource.POWER -> Color(0xFFFFB74D)
    ChartMetricSource.SOC -> Color(0xFF66BB6A)
    ChartMetricSource.SPEED -> Color(0xFFBA68C8)
}

private fun fmt(v: Float?): String = if (v == null || !v.isFinite()) "-" else String.format(Locale.US, "%.2f", v)
private const val CHART_WINDOW_MS = 60_000L
