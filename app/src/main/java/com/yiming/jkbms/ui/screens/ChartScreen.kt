package com.yiming.jkbms.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiming.jkbms.model.ChartMetricSource
import com.yiming.jkbms.model.TrendSample
import kotlin.math.max
import kotlin.math.min

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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val latestTs = samples.lastOrNull()?.timestampMs ?: 0L
    val liveSamples = if (latestTs == 0L) emptyList() else samples.filter { it.timestampMs >= latestTs - CHART_WINDOW_MS }
    val displaySamples = if (playback) samples else liveSamples
    val values = displaySamples.map { valueFromSource(it, chartSource) }
    val valueMin = values.minOrNull()
    val valueMax = values.maxOrNull()
    val metricColor = sourceColor(chartSource)
    val metaText = "总样本 ${samples.size} / 当前显示 ${displaySamples.size}"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartMetricSource.entries.forEach { source ->
                        val selected = source == chartSource
                        if (selected) {
                            Button(onClick = { onSourceChange(source) }) { Text(source.label) }
                        } else {
                            OutlinedButton(onClick = { onSourceChange(source) }) { Text(source.label) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(metaText, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onTogglePlayback) {
                            Text(if (playback) "退出回读" else "回读全程")
                        }
                        Button(onClick = onToggleRecord, enabled = !playback) {
                            Text(if (recording) "停止录制" else "开始录制")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (playback) "模式：全程回读（录制已暂停）" else "模式：实时窗口（60秒左移）",
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            ) {
                if (displaySamples.size < 2) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("样本不足，开始录制后查看曲线。", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val left = 56f
                        val top = 14f
                        val right = w - 12f
                        val bottom = h - 34f
                        val chartW = max(1f, right - left)
                        val chartH = max(1f, bottom - top)

                        val gridColor = if (isDark) Color(0xFF445063) else Color(0xFFB8C1CE)
                        val axisColor = if (isDark) Color(0xFF9AA4B2) else Color(0xFF5E6878)

                        for (i in 0..4) {
                            val y = top + chartH * i / 4f
                            drawLine(
                                color = gridColor.copy(alpha = 0.5f),
                                start = Offset(left, y),
                                end = Offset(right, y),
                                strokeWidth = if (i == 0 || i == 4) 2f else 1f
                            )
                        }
                        drawLine(color = axisColor, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2f)
                        drawLine(color = axisColor, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2f)

                        val minVal = valueMin ?: 0f
                        val maxVal = valueMax ?: 0f
                        val span = max(0.0001f, maxVal - minVal)
                        val pad = max(0.001f, span * 0.1f)
                        val yMin = minVal - pad
                        val yMax = maxVal + pad
                        val ySpan = max(0.0001f, yMax - yMin)

                        fun xAt(index: Int): Float {
                            val denom = max(1, displaySamples.size - 1)
                            return left + chartW * index / denom.toFloat()
                        }

                        fun yAt(v: Float): Float {
                            val t = (v - yMin) / ySpan
                            return bottom - (t * chartH)
                        }

                        val path = Path()
                        displaySamples.forEachIndexed { i, sample ->
                            val x = xAt(i)
                            val y = yAt(valueFromSource(sample, chartSource))
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = metricColor,
                            style = Stroke(width = 4f)
                        )

                        val maxIndex = values.indexOfFirst { it == maxVal }.coerceAtLeast(0)
                        val minIndex = values.indexOfFirst { it == minVal }.coerceAtLeast(0)
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = 5f,
                            center = Offset(xAt(maxIndex), yAt(maxVal))
                        )
                        drawCircle(
                            color = Color(0xFF448AFF),
                            radius = 5f,
                            center = Offset(xAt(minIndex), yAt(minVal))
                        )

                        val maxPointX = xAt(maxIndex)
                        val maxPointY = yAt(maxVal)
                        val minPointX = xAt(minIndex)
                        val minPointY = yAt(minVal)

                        val nc = drawContext.canvas.nativeCanvas
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 26f
                            color = android.graphics.Color.WHITE
                            typeface = android.graphics.Typeface.MONOSPACE
                        }

                        val maxLabel = "MAX ${fmt(maxVal)}"
                        val minLabel = "MIN ${fmt(minVal)}"
                        val labelGap = 10f
                        val pointRadius = 5f
                        val maxTextWidth = paint.measureText(maxLabel)
                        val minTextWidth = paint.measureText(minLabel)
                        val maxLabelX = min(max(maxPointX - (maxTextWidth / 2f), left + 2f), right - maxTextWidth - 2f)
                        val minLabelX = min(min(max(minPointX - (minTextWidth / 2f), left + 2f), right - minTextWidth - 2f), right - minTextWidth - 2f)
                        val maxLabelY = if (maxPointY <= top + 24f) {
                            maxPointY + paint.textSize + pointRadius + labelGap
                        } else {
                            maxPointY - pointRadius - labelGap
                        }
                        val minLabelY = if (minPointY <= top + 24f) {
                            minPointY + paint.textSize + pointRadius + labelGap
                        } else {
                            minPointY - pointRadius - labelGap
                        }
                        paint.color = android.graphics.Color.WHITE
                        nc.drawText(maxLabel, maxLabelX, maxLabelY, paint)
                        nc.drawText(minLabel, minLabelX, minLabelY, paint)

                        paint.color = if (isDark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY
                        paint.textSize = 24f
                        nc.drawText("${fmt(yMax)}", 6f, top + 8f, paint)
                        nc.drawText("${fmt(yMin)}", 6f, bottom + 8f, paint)
                    }
                }
            }
        }
    }
}

private fun valueFromSource(sample: TrendSample, source: ChartMetricSource): Float {
    return when (source) {
        ChartMetricSource.VOLTAGE -> sample.voltage
        ChartMetricSource.CURRENT -> sample.current
        ChartMetricSource.POWER -> sample.power
        ChartMetricSource.SOC -> sample.soc
    }
}

private fun sourceColor(source: ChartMetricSource): Color {
    return when (source) {
        ChartMetricSource.VOLTAGE -> Color(0xFF42A5F5)
        ChartMetricSource.CURRENT -> Color(0xFF26C6DA)
        ChartMetricSource.POWER -> Color(0xFFFFB74D)
        ChartMetricSource.SOC -> Color(0xFF66BB6A)
    }
}

private fun fmt(v: Float?): String = if (v == null || !v.isFinite()) "-" else String.format("%.2f", v)

private const val CHART_WINDOW_MS = 60_000L
