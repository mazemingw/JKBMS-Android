package com.yiming.jkbms.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    recording: Boolean,
    playback: Boolean,
    samples: List<TrendSample>,
    onSourceChange: (ChartMetricSource) -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    val latestTs = samples.lastOrNull()?.timestampMs ?: 0L
    val liveSamples = remember(samples, latestTs) {
        if (latestTs == 0L) emptyList() else samples.filter { it.timestampMs >= latestTs - CHART_WINDOW_MS }
    }
    val displaySamples = if (playback) samples else liveSamples
    val values = remember(displaySamples, chartSource) { displaySamples.map { valueFromSource(it, chartSource) } }
    val valueMin = values.minOrNull()
    val valueMax = values.maxOrNull()
    val metricColor = sourceColor(chartSource)
    val chartPoints = remember(displaySamples, chartSource) {
        displaySamples.map { EChartPoint(it.timestampMs, valueFromSource(it, chartSource)) }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartMetricSource.entries.forEach { source ->
                        if (source == chartSource) {
                            Button(onClick = { onSourceChange(source) }) { Text(source.label) }
                        } else {
                            OutlinedButton(onClick = { onSourceChange(source) }) { Text(source.label) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("总样本 ${samples.size} / 当前显示 ${displaySamples.size}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onTogglePlayback) { Text(if (playback) "退出回读" else "回读全程") }
                        Button(onClick = onToggleRecord, enabled = !playback) {
                            Text(if (recording) "停止录制" else "开始录制")
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (playback) "模式：全程回读（录制已暂停）" else "模式：实时窗口（60秒）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "MAX ${fmt(valueMax)}  MIN ${fmt(valueMin)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = metricColor
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            if (chartPoints.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("样本不足，开始录制后查看曲线。", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                EChartsLine(
                    points = chartPoints,
                    name = chartSource.label,
                    unit = chartSource.unit,
                    color = metricColor,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    decimals = if (chartSource == ChartMetricSource.SOC) 1 else 2,
                    showArea = false
                )
            }
        }
    }
}

private fun valueFromSource(sample: TrendSample, source: ChartMetricSource): Float = when (source) {
    ChartMetricSource.VOLTAGE -> sample.voltage
    ChartMetricSource.CURRENT -> sample.current
    ChartMetricSource.POWER -> sample.power
    ChartMetricSource.SOC -> sample.soc
}

private fun sourceColor(source: ChartMetricSource): Color = when (source) {
    ChartMetricSource.VOLTAGE -> Color(0xFF42A5F5)
    ChartMetricSource.CURRENT -> Color(0xFF26C6DA)
    ChartMetricSource.POWER -> Color(0xFFFFB74D)
    ChartMetricSource.SOC -> Color(0xFF66BB6A)
}

private fun fmt(v: Float?): String = if (v == null || !v.isFinite()) "-" else String.format(Locale.US, "%.2f", v)

private const val CHART_WINDOW_MS = 60_000L
