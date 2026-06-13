package com.yiming.jkbms.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.yiming.jkbms.model.BleDeviceUi
import com.yiming.jkbms.model.BottomTab
import com.yiming.jkbms.model.ChartMetricSource
import com.yiming.jkbms.model.GaugeMetricSource
import com.yiming.jkbms.model.GpsSignalState
import com.yiming.jkbms.model.ParsedMetrics
import com.yiming.jkbms.model.ThemeMode
import com.yiming.jkbms.model.TrendSample

class MainViewModel : ViewModel() {
    var passwordInput by mutableStateOf("")
    var statusText by mutableStateOf("待机")
    var scanButtonText by mutableStateOf("开始扫描")
    var devicesUi by mutableStateOf(listOf<BleDeviceUi>())
    var selectedAddress by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(BottomTab.INFO)
    var latestMetrics by mutableStateOf<ParsedMetrics?>(null)
    var mockChargingSample by mutableStateOf(false)
    var useAppFont by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.DARK)
    var showSpeedCard by mutableStateOf(true)
    var splitScreenInfoOnly by mutableStateOf(false)
    var gpsEnabled by mutableStateOf(false)
    var gpsSpeedKmh by mutableStateOf<Float?>(null)
    var gpsSignalState by mutableStateOf(GpsSignalState.OFF)
    var gaugeEnabled by mutableStateOf(false)
    var gaugeSource by mutableStateOf(GaugeMetricSource.POWER)
    var gaugeCurrentMax by mutableStateOf(60f)
    var gaugePowerMax by mutableStateOf(3000f)
    var gaugeVoltageMax by mutableStateOf(86f)
    var bleConnected by mutableStateOf(false)
    var bleSignalRssi by mutableStateOf<Int?>(null)
    var bleActivityFlash by mutableStateOf(false)
    var chartRecording by mutableStateOf(false)
    var chartPlayback by mutableStateOf(false)
    var chartSource by mutableStateOf(ChartMetricSource.VOLTAGE)
    var chartSamples by mutableStateOf(listOf<TrendSample>())
}
