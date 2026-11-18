package com.yiming.jkbms.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yiming.jkbms.model.DetailItem
import com.yiming.jkbms.model.EnergyUiState
import com.yiming.jkbms.model.GaugeMetricSource
import com.yiming.jkbms.model.GpsSignalState
import com.yiming.jkbms.model.ParsedMetrics
import com.yiming.jkbms.model.TripMotionState
import com.yiming.jkbms.ui.components.EChartPoint
import com.yiming.jkbms.ui.components.EChartPieSlice
import com.yiming.jkbms.ui.components.EChartsLine
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class SmokeParticle(
    val phase: Float,
    val speed: Float,
    val lift: Float,
    val wobble: Float,
    val size: Float
)

@Composable
fun InfoPage(
    modifier: Modifier = Modifier,
    metrics: ParsedMetrics?,
    energyState: EnergyUiState = EnergyUiState(),
    bleConnected: Boolean = false,
    bleRssi: Int? = null,
    bleActivityFlash: Boolean = false,
    showSpeedCard: Boolean = true,
    showEnergyPanel: Boolean = true,
    gpsSpeedKmh: Float? = null,
    gpsSignalState: GpsSignalState = GpsSignalState.OFF,
    onToggleGps: () -> Unit = {},
    gaugeEnabled: Boolean = false,
    gaugeSource: GaugeMetricSource = GaugeMetricSource.POWER,
    gaugeCurrentMax: Float = 60f,
    gaugePowerMax: Float = 3000f,
    gaugeVoltageMax: Float = 86f,
    onGaugeSourceChange: (GaugeMetricSource) -> Unit = {},
    compactTopMetricsOnly: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val hasData = metrics != null
    fun f1(v: Float?): String = if (v == null) "-" else format1(v)
    fun f3(v: Float?): String = if (v == null) "-" else format3(v)
    fun ft(v: Float?): String = if (v == null || v <= -199.9f) "-" else format1(v)
    fun fi(v: Int?): String = v?.toString() ?: "-"
    fun fl(v: Long?): String = v?.toString() ?: "-"
    fun fb(v: Boolean?): String = if (v == null) "-" else onOff(v)
    val batteryTypeText = inferBatteryTypeLabel(metrics)
    val metricGreen = Color(0xFF41CD52)
    val mosTempColor = mosTemperatureColor(metrics?.mosTemperature, metricGreen)
    val offColor = Color(0xFF9AA4B2)
    fun switchColor(v: Boolean?): Color = if (v == false) offColor else metricGreen
    val topCardTitleColor = colorScheme.onSurface
    val topCardSubtitleColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.62f else 0.50f)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!compactTopMetricsOnly) {
            TopStatusRow(
                runtimeText = if (metrics == null) "-" else formatDuration(metrics.totalRuntimeSeconds),
                bleConnected = bleConnected,
                bleRssi = bleRssi,
                bleActivityFlash = bleActivityFlash,
                showSpeedCard = showSpeedCard,
                gpsSpeedKmh = gpsSpeedKmh,
                gpsSignalState = gpsSignalState,
                onToggleGps = onToggleGps,
                isDark = isDark
            )
        }

        val powerHint = buildPowerHint(metrics)
        val energyHint = buildRemainingEnergyHint(metrics)
        val capacityHint = buildRemainingCapacityHint(metrics)
        val powerIconContainerColor = Color(0xFF81C784).copy(alpha = if (isDark) 0.50f else 0.42f)
        val topCardValueColor = metricGreen
        val topCardIconContainerColor = Color(0xFF81C784).copy(alpha = if (isDark) 0.50f else 0.42f)
        val topCardBadgeColor = if (isDark) Color.White.copy(alpha = 0.60f) else colorScheme.onSurface.copy(alpha = 0.55f)
        val topItems = buildTopMetricItems(
            metrics = metrics,
            powerHint = powerHint,
            energyHint = energyHint,
            voltageHint = capacityHint,
            topCardValueColor = topCardValueColor,
            topCardTitleColor = topCardTitleColor,
            topCardSubtitleColor = topCardSubtitleColor,
            topCardIconContainerColor = topCardIconContainerColor,
            powerIconContainerColor = powerIconContainerColor,
            topCardBadgeColor = topCardBadgeColor,
            gaugeCurrentMax = gaugeCurrentMax,
            gaugePowerMax = gaugePowerMax,
            gaugeVoltageMax = gaugeVoltageMax
        )
        if (gaugeEnabled) {
            TopGaugeLayout(
                items = topItems,
                source = gaugeSource,
                isDark = isDark,
                onGaugeSourceChange = onGaugeSourceChange
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BigMetricCard(item = topItems.first { it.source == GaugeMetricSource.CURRENT }, modifier = Modifier.weight(1f))
                BigMetricCard(item = topItems.first { it.source == GaugeMetricSource.POWER }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BigMetricCard(item = topItems.first { it.source == GaugeMetricSource.SOC }, modifier = Modifier.weight(1f))
                BigMetricCard(item = topItems.first { it.source == GaugeMetricSource.VOLTAGE }, modifier = Modifier.weight(1f))
            }
        }

        if (!compactTopMetricsOnly && showEnergyPanel) {
            EnergyOverviewCard(
                energyState = energyState,
                isDark = isDark,
                hasBmsData = metrics != null,
                gpsSignalState = gpsSignalState
            )
        }

        if (!compactTopMetricsOnly) {
            val alertText = metrics?.let { formatErrorBits(it.errorsBitmask) } ?: "无"
            Text("告警详情", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = alertText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (!compactTopMetricsOnly) {
            Text("板上指标", style = MaterialTheme.typography.titleMedium)
            val highlightedDetails = listOf(
                DetailItem("压差", "${f3(metrics?.deltaCellVoltage)} V"),
                DetailItem("均衡电流", "${f3(metrics?.balanceCurrent)} A"),
                DetailItem("平均电压", "${f3(metrics?.avgCellVoltage)} V"),
                DetailItem("功率MOS温度", "${ft(metrics?.mosTemperature)} °C", mosTempColor),
                DetailItem("电池温度", "${ft(metrics?.temp1)} / ${ft(metrics?.temp2)} °C"),
                DetailItem("剩余容量", "${f3(metrics?.capacityRemainingAh)} Ah")
            )
            val details = listOf(
                DetailItem("温度T3/T4", "${ft(metrics?.temp3)} / ${ft(metrics?.temp4)} °C"),
                DetailItem("SOH健康度", "${fi(metrics?.soh)} %"),
                DetailItem("循环次数", fl(metrics?.cycleCount)),
                DetailItem("电池类型", batteryTypeText),
                DetailItem("满充容量", "${f3(metrics?.fullChargeCapacityAh)} Ah"),
                DetailItem("充电MOS", fb(metrics?.chargingMosEnabled), switchColor(metrics?.chargingMosEnabled)),
                DetailItem("放电MOS", fb(metrics?.dischargingMosEnabled), switchColor(metrics?.dischargingMosEnabled)),
                DetailItem("预充", fb(metrics?.prechargingEnabled), switchColor(metrics?.prechargingEnabled)),
                DetailItem("均衡MOS", fb(metrics?.balancingEnabled), switchColor(metrics?.balancingEnabled)),
                DetailItem("加热", fb(metrics?.heatingEnabled), switchColor(metrics?.heatingEnabled)),
                DetailItem("应急时间", if (metrics == null) "-" else "${metrics.emergencyCountdownSeconds} s"),
                DetailItem("充电阶段ID", fi(metrics?.chargeStatusId)),
                DetailItem("告警掩码", if (metrics == null) "-" else "0x${metrics.errorsBitmask.toString(16).uppercase(Locale.US).padStart(8, '0')}")
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                highlightedDetails.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        HighlightMetricCard(
                            rowItems[0].title,
                            rowItems[0].value,
                            Modifier.weight(1f),
                            rowItems[0].valueColor
                        )
                        if (rowItems.size > 1) {
                            HighlightMetricCard(
                                rowItems[1].title,
                                rowItems[1].value,
                                Modifier.weight(1f),
                                rowItems[1].valueColor
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                details.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactMetricCard(
                            rowItems[0].title,
                            rowItems[0].value,
                            Modifier.weight(1f),
                            rowItems[0].valueColor
                        )
                        if (rowItems.size > 1) {
                            CompactMetricCard(
                                rowItems[1].title,
                                rowItems[1].value,
                                Modifier.weight(1f),
                                rowItems[1].valueColor
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (!compactTopMetricsOnly) {
            Text("电芯电压", style = MaterialTheme.typography.titleMedium)
            val shownCells = if (hasData) {
                metrics!!.cellVoltages.withIndex().filter { (_, v) -> v > 0f }
            } else {
                (0 until 24).map { i -> IndexedValue(i, 0f) }
            }
            val highestCell = shownCells.maxByOrNull { it.value }?.index
            val lowestCell = shownCells.minByOrNull { it.value }?.index
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shownCells.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (entry in rowItems) {
                            val idx = entry.index + 1
                            val v = entry.value
                            val valueColor = if (!hasData || shownCells.isEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else if (highestCell != null && lowestCell != null && highestCell != lowestCell && entry.index == highestCell) {
                                Color(0xFFFF5252)
                            } else if (highestCell != null && lowestCell != null && highestCell != lowestCell && entry.index == lowestCell) {
                                Color(0xFF448AFF)
                            } else {
                                metricGreen
                            }
                            CellMetricCard(
                                "Cell $idx",
                                if (hasData) "${format3(v)} V" else "-",
                                Modifier.weight(1f),
                                valueColor = valueColor
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigMetricCard(
    item: TopMetricItem,
    modifier: Modifier = Modifier,
) {
    val resolvedTitleColor = item.titleColor ?: MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.height(104.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val fill = item.sideFillProgress
            if (fill != null && fill > 0f) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background((item.sideFillColor ?: Color(0xFF448AFF)).copy(alpha = 0.28f))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = resolvedTitleColor)
                MetricValueText(
                    value = item.value,
                    unit = item.badgeText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = item.valueColor
                )
                if (item.subtitle != null || item.keepSubtitleSpace) {
                    Text(
                        text = item.subtitle?.ifBlank { " " } ?: " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = item.subtitleColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .background(item.iconContainerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.badgeText,
                    color = item.badgeColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private enum class EnergyCardMode(val label: String) {
    CHART("曲线"),
    TRIP_A("TRIP A"),
    TOTAL("TOTAL")
}

@Composable
private fun EnergyOverviewCard(
    energyState: EnergyUiState,
    isDark: Boolean,
    hasBmsData: Boolean,
    gpsSignalState: GpsSignalState
) {
    var mode by rememberSaveable { mutableStateOf(EnergyCardMode.TRIP_A) }
    val hasTripData = energyState.tripDistanceKm > 0f || energyState.tripDischargedAh > 0f || energyState.tripRecoveredAh > 0f
    val useTotalFallback = !hasTripData && energyState.totalDistanceKm > 0f
    val green = Color(0xFF41CD52)
    val chartPoints = remember(energyState.trend) {
        energyState.trend.map { EChartPoint(it.timestampMs, it.kmPerAh) }
    }
    val pieSlices = remember(energyState.tripDischargedAh, energyState.tripRecoveredAh) {
        listOf(
            EChartPieSlice("放电", energyState.tripDischargedAh, Color(0xFFFF9F43)),
            EChartPieSlice("回收", energyState.tripRecoveredAh, green)
        )
    }
    val readinessText = when {
        !hasBmsData -> "等待BMS数据"
        gpsSignalState == GpsSignalState.OFF -> "请开启GPS"
        gpsSignalState == GpsSignalState.SEARCHING -> "等待GPS定位"
        else -> null
    }
    val headlineDistance = when (mode) {
        EnergyCardMode.TOTAL -> energyState.totalDistanceKm
        EnergyCardMode.TRIP_A -> if (useTotalFallback) energyState.totalDistanceKm else energyState.tripDistanceKm
        EnergyCardMode.CHART -> energyState.tripDistanceKm
    }
    val nextMode = {
        mode = when (mode) {
            EnergyCardMode.CHART -> EnergyCardMode.TRIP_A
            EnergyCardMode.TRIP_A -> EnergyCardMode.TOTAL
            EnergyCardMode.TOTAL -> EnergyCardMode.CHART
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(174.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("能效", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatCompactDistance(headlineDistance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
                Text(
                    mode.label,
                    modifier = Modifier
                        .background(green.copy(alpha = if (isDark) 0.18f else 0.12f), RoundedCornerShape(14.dp))
                        .clickable(onClick = nextMode)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = green
                )
            }

            when (mode) {
                EnergyCardMode.TRIP_A -> EnergyStatsPanel(
                    distanceLabel = if (useTotalFallback) "累计里程" else if (energyState.isHistorical) "上次里程" else "本次里程",
                    distance = if (useTotalFallback) energyState.totalDistanceKm else energyState.tripDistanceKm,
                    energyLabel = if (useTotalFallback) "累计耗电" else if (energyState.isHistorical) "上次耗电" else "本次耗电",
                    netAh = if (useTotalFallback) energyState.totalNetAh else energyState.tripNetAh,
                    efficiencyLabel = if (useTotalFallback) "平均能效" else "单次能效",
                    efficiency = energyState.tripKmPerAh ?: energyState.totalKmPerAh,
                    footer = if (energyState.isHistorical) {
                        "上次行程 · 回收 ${formatAmpHours(energyState.tripRecoveredAh)} · 参考续航 ${formatRange(energyState.estimatedRangeKm)}"
                    } else {
                        "回收 ${formatAmpHours(energyState.tripRecoveredAh)} · 预计里程 ${formatRange(energyState.estimatedRangeKm)}"
                    },
                    green = green,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )

                EnergyCardMode.TOTAL -> EnergyStatsPanel(
                    distanceLabel = "总里程",
                    distance = energyState.totalDistanceKm,
                    energyLabel = "总耗电",
                    netAh = energyState.totalNetAh,
                    efficiencyLabel = "平均能效",
                    efficiency = energyState.totalKmPerAh,
                    footer = "累计回收 ${formatAmpHours(energyState.totalRecoveredAh)}  ·  预计里程 ${formatRange(energyState.estimatedRangeKm)}",
                    green = green,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )

                EnergyCardMode.CHART -> if (chartPoints.size >= 2) {
                    EChartsLine(
                        points = chartPoints,
                        name = "近60秒能效",
                        unit = "km/Ah",
                        color = green,
                        modifier = Modifier.fillMaxSize(),
                        decimals = 2,
                        showArea = true,
                        windowMs = 60_000L,
                        windowStepMs = 500L,
                        showExtrema = true,
                        pieSlices = pieSlices
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                readinessText != null -> readinessText
                                energyState.motionState == TripMotionState.IDLE -> "起步后开始生成能效曲线"
                                else -> "正在积累有效里程与耗电数据…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyStatsPanel(
    distanceLabel: String,
    distance: Float,
    energyLabel: String,
    netAh: Float,
    efficiencyLabel: String,
    efficiency: Float?,
    footer: String,
    green: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EnergyStat(distanceLabel, formatDistance(distance), Modifier.weight(1f), green)
            EnergyStat(energyLabel, formatAmpHours(netAh), Modifier.weight(1f), green)
            EnergyStat(efficiencyLabel, formatEfficiency(efficiency), Modifier.weight(1f), green)
        }
        Text(
            footer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.64f else 0.56f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun EnergyStat(label: String, value: String, modifier: Modifier, color: Color) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatAmpHours(value: Float): String =
    if (!value.isFinite()) "--" else String.format(Locale.US, "%.3f Ah", value)

private fun formatEfficiency(value: Float?): String =
    if (value == null || !value.isFinite()) "计算中" else String.format(Locale.US, "1Ah %.1fkm", value)

private fun formatRange(value: Float?): String =
    if (value == null || !value.isFinite()) "计算中" else String.format(Locale.US, "%.0f km", value)

private fun formatDistance(value: Float): String =
    if (!value.isFinite()) "-- km" else String.format(Locale.US, "%.2f km", value)

private fun formatCompactDistance(value: Float): String =
    if (!value.isFinite()) "--km" else String.format(Locale.US, "%.2fkm", value)

private data class TopMetricItem(
    val source: GaugeMetricSource,
    val title: String,
    val value: String,
    val subtitle: String? = null,
    val keepSubtitleSpace: Boolean = false,
    val valueColor: Color = Color(0xFF448AFF),
    val titleColor: Color? = null,
    val subtitleColor: Color = Color(0xFF448AFF),
    val iconContainerColor: Color = Color(0xFF448AFF),
    val badgeColor: Color = Color.White,
    val badgeText: String = "",
    val gaugeValue: Float = 0f,
    val gaugeMin: Float = 0f,
    val gaugeMax: Float = 100f,
    val gaugeOverflow: Boolean = false,
    val isDischargeState: Boolean = false,
    val sideFillProgress: Float? = null,
    val sideFillColor: Color? = null
)

private fun buildTopMetricItems(
    metrics: ParsedMetrics?,
    powerHint: String,
    energyHint: String?,
    voltageHint: String,
    topCardValueColor: Color,
    topCardTitleColor: Color,
    topCardSubtitleColor: Color,
    topCardIconContainerColor: Color,
    powerIconContainerColor: Color,
    topCardBadgeColor: Color,
    gaugeCurrentMax: Float,
    gaugePowerMax: Float,
    gaugeVoltageMax: Float
): List<TopMetricItem> {
    val currentMax = gaugeCurrentMax.coerceAtLeast(1f)
    val powerMax = gaugePowerMax.coerceAtLeast(1f)
    val voltageMin = 0f
    val voltageMax = gaugeVoltageMax.coerceAtLeast(1f)
    val currentValue = metrics?.current
    val powerValue = metrics?.power
    val currentDischarging = (currentValue ?: 0f) < -0.001f
    val powerDischarging = (powerValue ?: 0f) < -0.001f
    val socValue = computeSocPercent(metrics)
    val socFillColor = when {
        (socValue ?: 0f) < 10f -> Color(0xFFFF5252)
        (socValue ?: 0f) < 20f -> Color(0xFFFFA726)
        else -> Color(0xFF448AFF)
    }
    val voltageValue = metrics?.totalVoltage
    return listOf(
        TopMetricItem(
            source = GaugeMetricSource.CURRENT,
            title = "电流",
            value = if (currentValue == null) "-" else format3(currentValue),
            keepSubtitleSpace = true,
            valueColor = topCardValueColor,
            titleColor = topCardTitleColor,
            subtitleColor = topCardSubtitleColor,
            iconContainerColor = topCardIconContainerColor,
            badgeColor = topCardBadgeColor,
            badgeText = "A",
            gaugeValue = currentValue ?: 0f,
            gaugeMin = -currentMax,
            gaugeMax = currentMax,
            gaugeOverflow = abs(currentValue ?: 0f) > currentMax,
            isDischargeState = currentDischarging
        ),
        TopMetricItem(
            source = GaugeMetricSource.POWER,
            title = "电池功率",
            value = if (powerValue == null) "-" else format1(powerValue),
            subtitle = powerHint,
            valueColor = topCardValueColor,
            titleColor = topCardTitleColor,
            subtitleColor = topCardSubtitleColor,
            iconContainerColor = powerIconContainerColor,
            badgeColor = topCardBadgeColor,
            badgeText = "W",
            gaugeValue = powerValue ?: 0f,
            gaugeMin = -powerMax,
            gaugeMax = powerMax,
            gaugeOverflow = abs(powerValue ?: 0f) > powerMax,
            isDischargeState = powerDischarging
        ),
        TopMetricItem(
            source = GaugeMetricSource.SOC,
            title = "电池百分比",
            value = buildSocPercentText(metrics),
            subtitle = energyHint,
            valueColor = topCardValueColor,
            titleColor = topCardTitleColor,
            subtitleColor = topCardSubtitleColor,
            iconContainerColor = topCardIconContainerColor,
            badgeColor = topCardBadgeColor,
            badgeText = "%",
            gaugeValue = socValue ?: 0f,
            gaugeMin = 0f,
            gaugeMax = 100f,
            gaugeOverflow = (socValue ?: 0f) > 100f,
            sideFillProgress = ((socValue ?: 0f) / 100f).coerceIn(0f, 1f),
            sideFillColor = socFillColor
        ),
        TopMetricItem(
            source = GaugeMetricSource.VOLTAGE,
            title = "电压",
            value = if (voltageValue == null) "-" else format3(voltageValue),
            subtitle = voltageHint,
            valueColor = topCardValueColor,
            titleColor = topCardTitleColor,
            subtitleColor = topCardSubtitleColor,
            iconContainerColor = topCardIconContainerColor,
            badgeColor = topCardBadgeColor,
            badgeText = "V",
            gaugeValue = voltageValue ?: 0f,
            gaugeMin = voltageMin,
            gaugeMax = voltageMax,
            gaugeOverflow = (voltageValue ?: 0f) > voltageMax
        )
    )
}

@Composable
private fun TopGaugeLayout(
    items: List<TopMetricItem>,
    source: GaugeMetricSource,
    isDark: Boolean,
    onGaugeSourceChange: (GaugeMetricSource) -> Unit
) {
    val gaugeItem = items.firstOrNull { it.source == source } ?: return
    val sideItems = items.filter { it.source != source }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GaugeMetricCard(
            item = gaugeItem,
            isDark = isDark,
            modifier = Modifier
                .weight(2f)
                .height(216.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .height(216.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sideItems.forEach { item ->
                SmallTopMetricCard(
                    item = item,
                    modifier = Modifier.weight(1f),
                    onClick = { onGaugeSourceChange(item.source) }
                )
            }
        }
    }
}

@Composable
private fun GaugeMetricCard(
    item: TopMetricItem,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val rangeSize = (item.gaugeMax - item.gaugeMin).coerceAtLeast(1f)
    val zeroCentered = item.source == GaugeMetricSource.CURRENT || item.source == GaugeMetricSource.POWER
    val rawProgress = when (item.source) {
        GaugeMetricSource.VOLTAGE -> (item.gaugeValue - item.gaugeMin) / rangeSize
        GaugeMetricSource.CURRENT, GaugeMetricSource.POWER -> {
            0.5f - (item.gaugeValue / (item.gaugeMax.coerceAtLeast(1f) * 2f))
        }
        else -> item.gaugeValue / item.gaugeMax.coerceAtLeast(1f)
    }
    val rawTarget = rawProgress.coerceIn(0f, 1f)
    val target = if (!item.gaugeOverflow && zeroCentered && abs(item.gaugeValue) > 0.001f) {
        if (item.gaugeValue < 0f) rawTarget.coerceAtLeast(0.53f) else rawTarget.coerceAtMost(0.47f)
    } else if (!item.gaugeOverflow && item.isDischargeState && item.gaugeValue > 0f) {
        rawTarget.coerceAtLeast(0.03f)
    } else {
        rawTarget
    }
    val gaugeAccentColor = if (item.gaugeOverflow) {
        Color(0xFFFF3B30)
    } else if (item.isDischargeState) {
        Color(0xFFEF6C00)
    } else {
        Color(0xFF41CD52)
    }
    val gaugeValueColor = if (item.gaugeOverflow || item.isDischargeState) gaugeAccentColor else item.valueColor
    val gaugeTrackColor = if (isDark) Color(0xFF2B3643) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val tickMajorColor = if (isDark) Color(0xFF77879B) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
    val tickMinorColor = if (isDark) Color(0xFF4A5666) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    val gaugeRangeTextColor = if (isDark) Color(0xFF9AA4B2) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
    val smokeParticles = remember {
        List(24) { i ->
            val base = i + 1f
            SmokeParticle(
                phase = ((sin(base * 12.9898) * 43758.5453).toFloat()).mod(1f).let { if (it < 0f) it + 1f else it },
                speed = 1.05f + (((sin(base * 8.331 + 0.73) * 15731.743).toFloat()).mod(1f).let { if (it < 0f) it + 1f else it }) * 1.45f,
                lift = 0.55f + (((sin(base * 5.117 + 1.37) * 11369.917).toFloat()).mod(1f).let { if (it < 0f) it + 1f else it }) * 0.95f,
                wobble = 0.4f + (((sin(base * 3.713 + 2.11) * 6151.337).toFloat()).mod(1f).let { if (it < 0f) it + 1f else it }) * 1.4f,
                size = 0.7f + (((sin(base * 2.903 + 0.41) * 22441.213).toFloat()).mod(1f).let { if (it < 0f) it + 1f else it }) * 1.0f
            )
        }
    }
    val animatedProgress = remember { Animatable(0f) }
    var smokeClock by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var startedAtNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (startedAtNanos == 0L) startedAtNanos = frameTimeNanos
                val elapsedSeconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000f
                // Same motion rate as the original 0..10000 / 90s animation,
                // without a periodic restart that makes the particle field jump.
                smokeClock = elapsedSeconds * (10_000f / 90f)
            }
        }
    }
    val latestTarget by rememberUpdatedState(target)
    var initialized by remember { mutableStateOf(false) }
    var lastSource by remember { mutableStateOf(item.source) }
    var sourceSwitchAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(item.source) {
        if (!initialized) {
            animatedProgress.snapTo(target)
            initialized = true
            lastSource = item.source
            return@LaunchedEffect
        }

        if (item.source != lastSource) {
            sourceSwitchAnimating = true
            try {
                animatedProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 220f
                    )
                )
                animatedProgress.animateTo(
                    targetValue = latestTarget,
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = 78f,
                        visibilityThreshold = 0.001f
                    )
                )
            } finally {
                sourceSwitchAnimating = false
                lastSource = item.source
            }
        }
    }

    LaunchedEffect(target) {
        if (!initialized) return@LaunchedEffect
        if (sourceSwitchAnimating) return@LaunchedEffect
        if (item.gaugeOverflow) {
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = 92f,
                    visibilityThreshold = 0.001f
                )
            )
            return@LaunchedEffect
        }
        if (item.source == lastSource) {
            animatedProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = 78f,
                    visibilityThreshold = 0.001f
                )
            )
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
            ) {
                val startAngle = 150f
                val sweepAngle = 240f
                val progress = animatedProgress.value

                val center = Offset(size.width / 2f, size.height * 0.63f)
                val radius = size.minDimension * 0.47f
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                drawArc(
                    color = gaugeTrackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                val accentStartProgress = if (zeroCentered) minOf(progress, 0.5f) else 0f
                val accentSweepProgress = if (zeroCentered) abs(progress - 0.5f) else progress
                if (accentSweepProgress > 0.001f) {
                    drawArc(
                        color = gaugeAccentColor,
                        startAngle = startAngle + sweepAngle * accentStartProgress,
                        sweepAngle = sweepAngle * accentSweepProgress,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                val majorStep = 4
                for (i in 0..24) {
                    val angle = startAngle + sweepAngle * (i / 24f)
                    val radians = Math.toRadians(angle.toDouble())
                    val major = i % majorStep == 0
                    if (!isDark && !major) continue
                    val outerOffset = if (isDark) 8.dp.toPx() else 3.dp.toPx()
                    val majorInnerOffset = if (isDark) 12.dp.toPx() else 7.dp.toPx()
                    val minorInnerOffset = if (isDark) 8.dp.toPx() else 5.dp.toPx()
                    val outer = Offset(
                        x = center.x + (radius + outerOffset) * cos(radians).toFloat(),
                        y = center.y + (radius + outerOffset) * sin(radians).toFloat()
                    )
                    val inner = Offset(
                        x = center.x + (radius - if (major) majorInnerOffset else minorInnerOffset) * cos(radians).toFloat(),
                        y = center.y + (radius - if (major) majorInnerOffset else minorInnerOffset) * sin(radians).toFloat()
                    )
                    drawLine(
                        color = if (major) tickMajorColor else tickMinorColor,
                        start = inner,
                        end = outer,
                        strokeWidth = if (isDark) {
                            if (major) 2.dp.toPx() else 1.dp.toPx()
                        } else {
                            1.2.dp.toPx()
                        },
                        cap = StrokeCap.Round
                    )
                }

                val needleAngle = startAngle + sweepAngle * progress
                val needleRadians = Math.toRadians(needleAngle.toDouble())
                val needleTip = Offset(
                    x = center.x + (radius - 18.dp.toPx()) * cos(needleRadians).toFloat(),
                    y = center.y + (radius - 18.dp.toPx()) * sin(needleRadians).toFloat()
                )
                drawLine(
                    color = gaugeAccentColor,
                    start = center,
                    end = needleTip,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = gaugeAccentColor,
                    radius = 5.dp.toPx(),
                    center = center
                )

                // Particle smoke trail when gauge passes 50%, drifting left from needle tip.
                if (progress > 0.5f) {
                    val intensity = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val trailLength = radius * (0.84f + intensity * 0.46f)
                    val riseHeight = radius * (0.20f + intensity * 0.20f)
                    val fadeTarget = if (isDark) Color(0xFF8EA1B3) else Color(0xFFCBD5E1)
                    for (i in smokeParticles.indices) {
                        val particle = smokeParticles[i]
                        val t = ((smokeClock * 0.0028f * particle.speed) + particle.phase).mod(1f)
                        val x = needleTip.x - trailLength * t
                        val wobble = kotlin.math.sin((smokeClock * 0.013f + i) * (1.05f + particle.wobble)) * (radius * 0.022f)
                        val y = needleTip.y - riseHeight * t * particle.lift + wobble
                        val baseAlpha = (1f - t) * (0.12f + 0.46f * intensity)
                        val particleRadius = (1.0f + 3.0f * t + intensity * 0.9f + particle.size * 0.6f).dp.toPx()
                        val particleColor = lerp(gaugeAccentColor, fadeTarget, (t * 0.90f).coerceIn(0f, 1f))

                        // Halo layer (soft, quickly fades along trail).
                        drawCircle(
                            color = particleColor.copy(alpha = baseAlpha * 0.34f),
                            radius = particleRadius * (1.35f + 0.5f * intensity),
                            center = Offset(x, y)
                        )

                        // Core particle.
                        drawCircle(
                            color = particleColor.copy(alpha = baseAlpha),
                            radius = particleRadius,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = item.titleColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(
                        when {
                            item.gaugeOverflow -> Color(0xFFFF3B30).copy(alpha = 0.85f)
                            item.isDischargeState -> Color(0xFFEF6C00).copy(alpha = 0.78f)
                            else -> item.iconContainerColor
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.badgeText,
                    color = item.badgeColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MetricValueText(
                    value = item.value,
                    unit = item.badgeText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = gaugeValueColor,
                    textAlign = TextAlign.Center
                )
                val footerText = if (
                    (item.source == GaugeMetricSource.POWER || item.source == GaugeMetricSource.VOLTAGE) &&
                    !item.subtitle.isNullOrBlank()
                ) {
                    item.subtitle
                } else {
                    "${formatGaugeMax(item.gaugeMin)}  -  ${formatGaugeMax(item.gaugeMax)}"
                }
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = gaugeRangeTextColor
                )
            }
        }
    }
}

@Composable
private fun SmallTopMetricCard(
    item: TopMetricItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val fill = item.sideFillProgress
            if (fill != null && fill > 0f) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background((item.sideFillColor ?: Color(0xFF448AFF)).copy(alpha = 0.28f))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = item.titleColor ?: MaterialTheme.colorScheme.onSurface
                )
                    MetricValueText(
                        value = item.value,
                        unit = item.badgeText,
                        style = MaterialTheme.typography.titleLarge,
                        color = item.valueColor
                    )
                    Text(
                        item.subtitle?.ifBlank { " " } ?: " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = item.subtitleColor
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .background(item.iconContainerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.badgeText,
                        color = item.badgeColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun formatGaugeMax(v: Float): String = if (abs(v) >= 100f) v.toInt().toString() else format1(v)

@Composable
private fun HighlightMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Card(
        modifier = modifier.height(42.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$title：",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: Color(0xFF41CD52),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$title：", style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor ?: Color(0xFF41CD52)
            )
        }
    }
}

@Composable
private fun CellMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
        }
    }
}

@Composable
private fun TopStatusRow(
    runtimeText: String,
    bleConnected: Boolean,
    bleRssi: Int?,
    bleActivityFlash: Boolean,
    showSpeedCard: Boolean,
    gpsSpeedKmh: Float?,
    gpsSignalState: GpsSignalState,
    onToggleGps: () -> Unit,
    isDark: Boolean
) {
    val screenConnectionText = "未连接"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BluetoothStatusIndicator(
                            connected = bleConnected,
                            rssi = bleRssi,
                            flash = bleActivityFlash
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("运行时间：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text(runtimeText, style = MaterialTheme.typography.titleSmall, color = Color(0xFF41CD52))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BluetoothStatusIndicator(
                            connected = false,
                            rssi = null,
                            flash = false,
                            icon = Icons.Outlined.DeveloperBoard
                        )
                        Text(
                            "蓝牙屏幕：",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        screenConnectionText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        if (showSpeedCard) {
            val searching = gpsSignalState == GpsSignalState.SEARCHING
            val ready = gpsSignalState == GpsSignalState.READY
            val blinkTransition = rememberInfiniteTransition(label = "gps-search")
            val blink by blinkTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "gps-search-blink"
            )
            val speedIconColor = when {
                searching -> lerp(Color(0xFF677181), Color(0xFFFFC107), blink)
                ready -> if (isDark) Color(0xFF41CD52) else Color(0xFF2E7D32)
                else -> Color(0xFF677181)
            }
            val speedValueText = if (ready) {
                String.format(Locale.US, "%.1f", (gpsSpeedKmh ?: 0f).coerceAtLeast(0f))
            } else {
                null
            }
            Card(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight()
                    .clickable { onToggleGps() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    if (speedValueText != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MetricValueText(
                                value = speedValueText,
                                unit = "km/h",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (isDark) Color(0xFF41CD52) else Color(0xFF2E7D32),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.SatelliteAlt,
                            contentDescription = "gps-none",
                            tint = speedIconColor,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleLineInfoCard(
    title: String,
    value: String,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("$title：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color(0xFF41CD52))
        }
    }
}

@Composable
private fun BluetoothStatusIndicator(
    connected: Boolean,
    rssi: Int?,
    flash: Boolean,
    icon: ImageVector = Icons.Rounded.Bluetooth
) {
    val iconColor = when {
        !connected -> Color(0xFF9AA4B2)
        flash -> Color(0xFF448AFF)
        else -> Color(0xFF41CD52)
    }
    val signalColor = if (connected) Color(0xFF41CD52) else Color(0xFF677181)
    val level = signalLevelFromRssi(rssi)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "蓝牙状态",
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        SignalBars(
            level = level,
            activeColor = signalColor,
            inactiveColor = Color(0xFF3A4452),
            connected = connected
        )
    }
}

@Composable
private fun SignalBars(
    level: Int,
    activeColor: Color,
    inactiveColor: Color,
    connected: Boolean
) {
    val barHeights: List<Dp> = listOf(5.dp, 7.dp, 9.dp, 11.dp)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        barHeights.forEachIndexed { index, barHeight ->
            val enabled = connected && index < level
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = barHeight)
                    .background(if (enabled) activeColor else inactiveColor, CircleShape)
            )
        }
    }
}

private fun signalLevelFromRssi(rssi: Int?): Int {
    if (rssi == null) return 0
    return when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
    }
}

private fun onOff(enabled: Boolean): String = if (enabled) "开" else "关"
private fun format1(v: Float): String = String.format(Locale.US, "%.1f", v)
private fun format3(v: Float): String = String.format(Locale.US, "%.3f", v)

private fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d天 %02d:%02d:%02d", days, hours, minutes, secs)
}

private fun buildPowerHint(metrics: ParsedMetrics?): String {
    if (metrics == null) return "-"
    val power = metrics.power
    if (abs(power) < 1f) return "-"

    val hours = if (power < 0f) {
        val energyWh = metrics.capacityRemainingAh * metrics.totalVoltage
        if (energyWh <= 0f) return "-"
        energyWh / abs(power)
    } else {
        val remainingAh = (metrics.fullChargeCapacityAh - metrics.capacityRemainingAh).coerceAtLeast(0f)
        val energyWh = remainingAh * metrics.totalVoltage
        if (energyWh <= 0f) return "充电即将完成"
        energyWh / power
    }
    if (!hours.isFinite() || hours <= 0f) return "-"

    val totalMinutes = (hours * 60f).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (power < 0f) "放电剩余 ${h}h${m}m" else "充电剩余 ${h}h${m}m"
}

private fun buildRemainingEnergyHint(metrics: ParsedMetrics?): String? {
    if (metrics == null) return "剩余电量 -"
    val kwh = (metrics.capacityRemainingAh * metrics.totalVoltage) / 1000f
    if (!kwh.isFinite() || kwh < 0f) return "剩余电量 -"
    return "剩余电量 ${String.format(Locale.US, "%.2f", kwh)} kWh"
}

private fun buildRemainingCapacityHint(metrics: ParsedMetrics?): String {
    if (metrics == null) return "剩余容量 -"
    return "剩余容量 ${format3(metrics.capacityRemainingAh)} Ah"
}

@Composable
private fun MetricValueText(
    value: String,
    unit: String,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign = TextAlign.Start
) {
    val text = remember(value, unit) { buildBigMetricValueText("$value\u00A0$unit") }
    val measurer = rememberTextMeasurer()
    var availablePx by remember { mutableStateOf(0) }
    val boldStyle = style.copy(fontWeight = FontWeight.Bold)
    val measuredPx = measurer.measure(text, boldStyle, softWrap = false).size.width.coerceAtLeast(1)
    val scale = if (availablePx > 0) (availablePx.toFloat() / measuredPx).coerceIn(0.1f, 1f) else 1f
    Box(Modifier.fillMaxWidth().onSizeChanged { availablePx = it.width }) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = boldStyle.copy(fontSize = boldStyle.fontSize * scale),
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign
        )
    }
}

private fun buildBigMetricValueText(value: String): AnnotatedString {
    val numberEnd = value.indexOfFirst { it.isWhitespace() }.takeIf { it > 0 } ?: value.length
    val numberPart = value.substring(0, numberEnd)
    val dotIndex = numberPart.indexOf('.')
    var decimalEnd = dotIndex + 1
    while (decimalEnd < numberPart.length && numberPart[decimalEnd].isDigit()) {
        decimalEnd++
    }
    return buildAnnotatedString {
        if (dotIndex >= 0 && decimalEnd > dotIndex + 1) {
            append(numberPart.substring(0, dotIndex + 1))
            withStyle(SpanStyle(fontSize = 0.72.em)) {
                append(numberPart.substring(dotIndex + 1, decimalEnd))
            }
            append(numberPart.substring(decimalEnd))
        } else {
            append(numberPart)
        }
        withStyle(SpanStyle(fontSize = 0.5.em)) {
            append(value.substring(numberEnd))
        }
    }
}

private fun buildSocPercentText(metrics: ParsedMetrics?): String {
    val soc = computeSocPercent(metrics) ?: return "-"
    return String.format(Locale.US, "%.3f", soc)
}

private fun computeSocPercent(metrics: ParsedMetrics?): Float? {
    if (metrics == null) return null
    if (metrics.fullChargeCapacityAh <= 0.01f) return null
    return ((metrics.capacityRemainingAh / metrics.fullChargeCapacityAh) * 100f).coerceIn(0f, 100f)
}

private enum class BatteryType { TERNARY, LFP, LTO, UNKNOWN }

private fun inferBatteryType(metrics: ParsedMetrics?): BatteryType {
    if (metrics == null) return BatteryType.UNKNOWN
    val cells = metrics.enabledCellCount
    if (cells <= 0) return BatteryType.UNKNOWN
    val perCellVoltage = metrics.totalVoltage / cells
    val maxCell = metrics.maxCellVoltage
    val minCell = metrics.minCellVoltage
    // Keep ternary classification stable under transient load sag.
    // Only classify as LFP when both average and peak cell voltage are clearly in LFP band.
    if (maxCell >= 3.70f || perCellVoltage >= 3.62f) return BatteryType.TERNARY
    if (minCell > 0f && minCell <= 2.90f) return BatteryType.LTO
    return when {
        perCellVoltage < 2.90f -> BatteryType.LTO
        perCellVoltage < 3.45f && maxCell < 3.55f -> BatteryType.LFP
        else -> BatteryType.TERNARY
    }
}

private fun inferBatteryTypeLabel(metrics: ParsedMetrics?): String {
    if (metrics == null) return "-"
    if (metrics.soc < 20) return "SOC<20%，不可信"
    return when (inferBatteryType(metrics)) {
        BatteryType.TERNARY -> "三元"
        BatteryType.LFP -> "铁锂"
        BatteryType.LTO -> "钛酸锂"
        BatteryType.UNKNOWN -> "未知"
    }
}

private fun mosTemperatureColor(temp: Float?, normalColor: Color): Color {
    if (temp == null || temp <= -100f) return normalColor
    return when {
        temp >= 75f -> Color(0xFFFF5252)
        temp >= 60f -> Color(0xFFFFB74D)
        else -> normalColor
    }
}

private fun formatErrorBits(mask: Long): String {
    if (mask == 0L) return "无"
    val active = mutableListOf<String>()
    for (i in ERROR_LABELS.indices) {
        if (((mask shr i) and 0x01L) == 0x01L) {
            val label = ERROR_LABELS[i]
            if (label.isNotBlank()) active.add(label)
        }
    }
    return if (active.isEmpty()) "未知告警($mask)" else active.joinToString(" | ")
}

private val ERROR_LABELS = arrayOf(
    "线阻异常", "MOS过温", "电芯数量与设置不符", "", "电池满充", "整包过压", "充电过流", "充电短路",
    "充电过温", "充电低温", "协处理器通信异常", "单体欠压", "整包欠压", "放电过流", "放电短路", "放电过温",
    "充电MOS异常", "放电MOS异常", "GPS断开", "需修改密码", "放电打开失败", "电池过温", "温度传感器异常",
    "PCL模块异常", "SCP释放失败", "放电OCP II", "放电OCP III", "放电低温告警", "GPS远程锁定", "", "", ""
)
