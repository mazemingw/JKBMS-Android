package com.yiming.jkbms.model

import androidx.compose.ui.graphics.Color

enum class BottomTab(val label: String) {
    INFO("信息"),
    BLUETOOTH("蓝牙"),
    CHART("图表"),
    SETTINGS("设置")
}

enum class GaugeMetricSource(val label: String) {
    CURRENT("电流"),
    POWER("电池功率"),
    SOC("电池百分比"),
    VOLTAGE("电压")
}

enum class ThemeMode(val label: String) {
    DARK("夜间"),
    LIGHT("日间"),
    SYSTEM("跟随系统")
}

enum class GpsSignalState {
    OFF,
    SEARCHING,
    READY
}

enum class ChartMetricSource(val label: String, val unit: String) {
    VOLTAGE("电压", "V"),
    CURRENT("电流", "A"),
    POWER("电池功率", "W"),
    SOC("电池百分比", "%")
}

data class TrendSample(
    val timestampMs: Long,
    val voltage: Float,
    val current: Float,
    val power: Float,
    val soc: Float
)

data class BleDeviceUi(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasJkService: Boolean = false
)

data class ParsedMetrics(
    val totalVoltage: Float,
    val current: Float,
    val power: Float,
    val soc: Int,
    val soh: Int,
    val mosTemperature: Float,
    val temp1: Float,
    val temp2: Float,
    val temp3: Float,
    val temp4: Float,
    val temp5: Float,
    val balanceCurrent: Float,
    val capacityRemainingAh: Float,
    val fullChargeCapacityAh: Float,
    val cycleCount: Long,
    val totalCycleCapacityAh: Float,
    val totalRuntimeSeconds: Long,
    val emergencyCountdownSeconds: Int,
    val chargeStatusId: Int,
    val chargingMosEnabled: Boolean,
    val dischargingMosEnabled: Boolean,
    val prechargingEnabled: Boolean,
    val balancingEnabled: Boolean,
    val heatingEnabled: Boolean,
    val enabledCellCount: Int,
    val minCellVoltage: Float,
    val maxCellVoltage: Float,
    val avgCellVoltage: Float,
    val deltaCellVoltage: Float,
    val minCellIndex: Int,
    val maxCellIndex: Int,
    val errorsBitmask: Long,
    val cellVoltages: List<Float>
)

data class DetailItem(
    val title: String,
    val value: String,
    val valueColor: Color? = null
)
