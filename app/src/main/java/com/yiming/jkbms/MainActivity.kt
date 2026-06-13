package com.yiming.jkbms

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yiming.jkbms.model.BleDeviceUi
import com.yiming.jkbms.model.BottomTab
import com.yiming.jkbms.model.ChartMetricSource
import com.yiming.jkbms.model.GaugeMetricSource
import com.yiming.jkbms.model.GpsSignalState
import com.yiming.jkbms.model.ParsedMetrics
import com.yiming.jkbms.model.ThemeMode
import com.yiming.jkbms.model.TrendSample
import com.yiming.jkbms.ui.screens.BluetoothPage
import com.yiming.jkbms.ui.screens.ChartPage
import com.yiming.jkbms.ui.screens.InfoPage
import com.yiming.jkbms.ui.screens.SettingsPage
import com.yiming.jkbms.ui.theme.JkBmsTheme
import com.yiming.jkbms.viewmodel.MainViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private data class BleDeviceItem(
        val device: BluetoothDevice,
        var rssi: Int,
        var name: String,
        var hasJkService: Boolean = false
    )

    private val uiHandler = Handler(Looper.getMainLooper())
    private val viewModel by viewModels<MainViewModel>()

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var locationManager: LocationManager
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private val deviceItems = mutableListOf<BleDeviceItem>()
    private var selectedDevice: BluetoothDevice? = null
    private var refreshScheduled = false

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var authenticated = false
    private var currentFrameCounter: Byte = 0
    private val frameBuffer = mutableListOf<Byte>()
    private var autoReconnectAddress: String? = null
    private var autoConnecting = false
    private var connectedAddress: String? = null

    private var passwordInput: String
        get() = viewModel.passwordInput
        set(value) {
            viewModel.passwordInput = value
        }
    private var statusText: String
        get() = viewModel.statusText
        set(value) {
            viewModel.statusText = value
        }
    private var scanButtonText: String
        get() = viewModel.scanButtonText
        set(value) {
            viewModel.scanButtonText = value
        }
    private var devicesUi: List<BleDeviceUi>
        get() = viewModel.devicesUi
        set(value) {
            viewModel.devicesUi = value
        }
    private var selectedAddress: String?
        get() = viewModel.selectedAddress
        set(value) {
            viewModel.selectedAddress = value
        }
    private var selectedTab: BottomTab
        get() = viewModel.selectedTab
        set(value) {
            viewModel.selectedTab = value
        }
    private var latestMetrics: ParsedMetrics?
        get() = viewModel.latestMetrics
        set(value) {
            viewModel.latestMetrics = value
        }
    private var mockChargingSample: Boolean
        get() = viewModel.mockChargingSample
        set(value) {
            viewModel.mockChargingSample = value
        }
    private var useAppFont: Boolean
        get() = viewModel.useAppFont
        set(value) {
            viewModel.useAppFont = value
        }
    private var themeMode: ThemeMode
        get() = viewModel.themeMode
        set(value) {
            viewModel.themeMode = value
        }
    private var showSpeedCard: Boolean
        get() = viewModel.showSpeedCard
        set(value) {
            viewModel.showSpeedCard = value
        }
    private var splitScreenInfoOnly: Boolean
        get() = viewModel.splitScreenInfoOnly
        set(value) {
            viewModel.splitScreenInfoOnly = value
        }
    private var gpsEnabled: Boolean
        get() = viewModel.gpsEnabled
        set(value) {
            viewModel.gpsEnabled = value
        }
    private var gpsSpeedKmh: Float?
        get() = viewModel.gpsSpeedKmh
        set(value) {
            viewModel.gpsSpeedKmh = value
        }
    private var gpsSignalState: GpsSignalState
        get() = viewModel.gpsSignalState
        set(value) {
            viewModel.gpsSignalState = value
        }
    private var gaugeEnabled: Boolean
        get() = viewModel.gaugeEnabled
        set(value) {
            viewModel.gaugeEnabled = value
        }
    private var gaugeSource: GaugeMetricSource
        get() = viewModel.gaugeSource
        set(value) {
            viewModel.gaugeSource = value
        }
    private var gaugeCurrentMax: Float
        get() = viewModel.gaugeCurrentMax
        set(value) {
            viewModel.gaugeCurrentMax = value
        }
    private var gaugePowerMax: Float
        get() = viewModel.gaugePowerMax
        set(value) {
            viewModel.gaugePowerMax = value
        }
    private var gaugeVoltageMax: Float
        get() = viewModel.gaugeVoltageMax
        set(value) {
            viewModel.gaugeVoltageMax = value
        }
    private var bleConnected: Boolean
        get() = viewModel.bleConnected
        set(value) {
            viewModel.bleConnected = value
        }
    private var bleSignalRssi: Int?
        get() = viewModel.bleSignalRssi
        set(value) {
            viewModel.bleSignalRssi = value
        }
    private var bleActivityFlash: Boolean
        get() = viewModel.bleActivityFlash
        set(value) {
            viewModel.bleActivityFlash = value
        }
    private var chartRecording: Boolean
        get() = viewModel.chartRecording
        set(value) {
            viewModel.chartRecording = value
        }
    private var chartPlayback: Boolean
        get() = viewModel.chartPlayback
        set(value) {
            viewModel.chartPlayback = value
        }
    private var chartSource: ChartMetricSource
        get() = viewModel.chartSource
        set(value) {
            viewModel.chartSource = value
        }
    private var chartSamples: List<TrendSample>
        get() = viewModel.chartSamples
        set(value) {
            viewModel.chartSamples = value
        }

    private val pollTask = object : Runnable {
        override fun run() {
            if (gatt != null && authenticated) {
                sendCommand(COMMAND_CELL_INFO)
                uiHandler.postDelayed(this, 3000)
            }
        }
    }

    private val chartRecordTask = object : Runnable {
        override fun run() {
            if (!chartRecording) return
            latestMetrics?.let { metrics ->
                appendTrendSample(
                    TrendSample(
                        timestampMs = System.currentTimeMillis(),
                        voltage = metrics.totalVoltage,
                        current = metrics.current,
                        power = metrics.power,
                        soc = metrics.soc.toFloat()
                    )
                )
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }
    private var mockDriveTick: Int = 0
    private var mockDriveSoc: Float = 23.0f
    private var mockRuntimeSeconds: Long = 2_963_445L
    private var mockGpsSpeedKmhValue: Float = 0f
    private var lastMockPhase: String? = null
    private val mockDriveTask = object : Runnable {
        override fun run() {
            if (!mockChargingSample) return
            val tick = mockDriveTick++
            val phase = mockPhaseName(tick % 180)
            if (phase != lastMockPhase) {
                lastMockPhase = phase
                showToast("MOCK阶段：$phase")
            }
            val metrics = buildMockDrivingMetrics(tick)
            latestMetrics = metrics
            updateMockGpsState(tick)
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val refreshDevicesTask = Runnable {
        refreshScheduled = false
        refreshDeviceList()
    }

    private val clearBleFlashTask = Runnable { bleActivityFlash = false }
    private var lastBleDataAtMs: Long = 0L
    private var bleAutoReconnecting = false
    private val bleDataWatchdogTask = object : Runnable {
        override fun run() {
            if (!bleConnected || !authenticated || gatt == null) return
            val staleMs = System.currentTimeMillis() - lastBleDataAtMs
            val rssiOk = (bleSignalRssi ?: Int.MIN_VALUE) >= BLE_RECONNECT_RSSI_MIN_DBM
            if (!bleAutoReconnecting && staleMs >= BLE_DATA_TIMEOUT_MS && rssiOk) {
                triggerBleAutoReconnect()
                return
            }
            uiHandler.postDelayed(this, BLE_WATCHDOG_INTERVAL_MS)
        }
    }
    private var gpsUpdating = false
    private var locationPermissionPrompted = false
    private var lastGpsUpdateAtMs: Long = 0L
    private val gpsTimeoutTask = object : Runnable {
        override fun run() {
            if (!showSpeedCard || !gpsUpdating) return
            val stale = System.currentTimeMillis() - lastGpsUpdateAtMs > 9000L
            if (stale) {
                gpsSignalState = GpsSignalState.SEARCHING
                gpsSpeedKmh = 0f
            }
            uiHandler.postDelayed(this, 3000)
        }
    }
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastGpsUpdateAtMs = System.currentTimeMillis()
            val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
            gpsSpeedKmh = speed
            gpsSignalState = when {
                !location.hasAccuracy() -> GpsSignalState.SEARCHING
                location.accuracy <= 60f -> GpsSignalState.READY
                else -> GpsSignalState.SEARCHING
            }
        }

        override fun onProviderDisabled(provider: String) {
            gpsSignalState = if (gpsEnabled) GpsSignalState.SEARCHING else GpsSignalState.OFF
            gpsSpeedKmh = if (gpsEnabled) 0f else null
        }
    }

    private val pollRssiTask = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            val currentGatt = gatt
            if (currentGatt != null && bleConnected && hasAllRequiredPermissions()) {
                currentGatt.readRemoteRssi()
                uiHandler.postDelayed(this, 2000)
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                ensureBluetoothEnabledAndScan()
            } else {
                setStatus("缺少蓝牙权限，无法扫描。")
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isBluetoothEnabled()) startScan() else setStatus("蓝牙未开启。")
        }
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                locationPermissionPrompted = false
                applyGpsTrackingState()
            } else {
                gpsEnabled = false
                gpsSignalState = GpsSignalState.OFF
                gpsSpeedKmh = null
                showToast("GPS权限未授权，已关闭测速")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        val lastSuccessPassword = prefs.getString(PREF_LAST_SUCCESS_PASSWORD, "") ?: ""
        passwordInput = if (lastSuccessPassword.isNotBlank()) lastSuccessPassword else (prefs.getString(PREF_LAST_PASSWORD, "") ?: "")
        useAppFont = prefs.getBoolean(PREF_USE_APP_FONT, true)
        themeMode = parseThemeMode(prefs.getString(PREF_THEME_MODE, null))
        applySystemBarAppearance()
        showSpeedCard = prefs.getBoolean(PREF_SHOW_SPEED_CARD, true)
        splitScreenInfoOnly = prefs.getBoolean(PREF_SPLIT_SCREEN_INFO_ONLY, false)
        gpsEnabled = prefs.getBoolean(PREF_GPS_ENABLED, false)
        gaugeEnabled = prefs.getBoolean(PREF_GAUGE_ENABLED, false)
        gaugeSource = parseGaugeSource(prefs.getString(PREF_GAUGE_SOURCE, null))
        gaugeCurrentMax = parsePositiveFloat(prefs.getFloat(PREF_GAUGE_CURRENT_MAX, 60f), 60f)
        gaugePowerMax = parsePositiveFloat(prefs.getFloat(PREF_GAUGE_POWER_MAX, 3000f), 3000f)
        gaugeVoltageMax = parsePositiveFloat(prefs.getFloat(PREF_GAUGE_VOLTAGE_MAX, 86f), 86f)
        autoReconnectAddress = prefs.getString(PREF_LAST_SUCCESS_DEVICE_ADDRESS, null)?.takeIf { it.isNotBlank() }

        setContent {
            JkBmsTheme(useAppFont = useAppFont, themeMode = themeMode) {
                val configuration = LocalConfiguration.current
                val isSmallWindow = configuration.screenWidthDp < 700 || configuration.screenHeightDp < 560 ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this@MainActivity.isInMultiWindowMode)
                val splitInfoActive = splitScreenInfoOnly && isSmallWindow
                Scaffold(
                    floatingActionButton = {
                        if (!splitInfoActive && selectedTab == BottomTab.INFO) {
                            FloatingActionButton(
                                onClick = {
                                    if (mockChargingSample) {
                                        stopMockDriving()
                                    } else {
                                        startMockDriving()
                                    }
                                }
                            ) {
                                Text(if (mockChargingSample) "MOCK ON" else "MOCK")
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == BottomTab.INFO,
                                onClick = { selectedTab = BottomTab.INFO },
                                icon = {},
                                label = { Text(BottomTab.INFO.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == BottomTab.BLUETOOTH,
                                onClick = {
                                    selectedTab = BottomTab.BLUETOOTH
                                    if (!scanning) requestScanPermissionsAndStart()
                                },
                                icon = {},
                                label = { Text(BottomTab.BLUETOOTH.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == BottomTab.CHART,
                                onClick = { selectedTab = BottomTab.CHART },
                                icon = {},
                                label = { Text(BottomTab.CHART.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == BottomTab.SETTINGS,
                                onClick = { selectedTab = BottomTab.SETTINGS },
                                icon = {},
                                label = { Text(BottomTab.SETTINGS.label) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .navigationBarsPadding()
                    ) {
                        when (selectedTab) {
                            BottomTab.INFO -> InfoPage(
                                    modifier = Modifier.fillMaxSize(),
                                    metrics = latestMetrics,
                                    bleConnected = bleConnected,
                                    bleRssi = bleSignalRssi,
                                    bleActivityFlash = bleActivityFlash,
                                    showSpeedCard = showSpeedCard,
                                    gpsSpeedKmh = gpsSpeedKmh,
                                    gpsSignalState = gpsSignalState,
                                    onToggleGps = {
                                        gpsEnabled = !gpsEnabled
                                        prefs.edit().putBoolean(PREF_GPS_ENABLED, gpsEnabled).apply()
                                        applyGpsTrackingState()
                                        showToast(if (gpsEnabled) "GPS测速已开启" else "GPS测速已关闭")
                                    },
                                    gaugeEnabled = gaugeEnabled,
                                    gaugeSource = gaugeSource,
                                    gaugeCurrentMax = gaugeCurrentMax,
                                    gaugePowerMax = gaugePowerMax,
                                    gaugeVoltageMax = gaugeVoltageMax,
                                    onGaugeSourceChange = {
                                        gaugeSource = it
                                        prefs.edit().putString(PREF_GAUGE_SOURCE, it.name).apply()
                                    },
                                    compactTopMetricsOnly = splitInfoActive
                                )
                            BottomTab.BLUETOOTH -> BluetoothPage(
                                modifier = Modifier.fillMaxSize(),
                                passwordInput = passwordInput,
                                onPasswordChange = {
                                    passwordInput = it
                                    prefs.edit().putString(PREF_LAST_PASSWORD, it).apply()
                                },
                                scanButtonText = scanButtonText,
                                devicesUi = devicesUi,
                                selectedAddress = selectedAddress,
                                statusText = statusText,
                                onToggleScan = {
                                    if (scanning) stopScan() else requestScanPermissionsAndStart()
                                },
                                onConnect = {
                                    val target = selectedDevice
                                    if (target == null) setStatus("请先选择一个设备。") else connectToDevice(target)
                                },
                                onSelectDevice = { item ->
                                    selectedAddress = item.address
                                    selectedDevice = deviceItems.firstOrNull { it.device.address == item.address }?.device
                                    setStatus("已选择: ${item.name} (${item.address})")
                                }
                            )
                            BottomTab.CHART -> ChartPage(
                                modifier = Modifier.fillMaxSize(),
                                chartSource = chartSource,
                                recording = chartRecording,
                                playback = chartPlayback,
                                samples = chartSamples,
                                onSourceChange = { chartSource = it },
                                onToggleRecord = {
                                    if (chartRecording) {
                                        stopChartRecording()
                                    } else {
                                        startChartRecording()
                                    }
                                },
                                onTogglePlayback = {
                                    chartPlayback = !chartPlayback
                                    if (chartPlayback && chartRecording) {
                                        stopChartRecording(silent = true)
                                    }
                                }
                            )
                            BottomTab.SETTINGS -> SettingsPage(
                                modifier = Modifier.fillMaxSize(),
                                useAppFont = useAppFont,
                                onFontToggle = {
                                    useAppFont = it
                                    prefs.edit().putBoolean(PREF_USE_APP_FONT, it).apply()
                                },
                                themeMode = themeMode,
                                onThemeModeChange = {
                                    themeMode = it
                                    prefs.edit().putString(PREF_THEME_MODE, it.name).apply()
                                    applySystemBarAppearance()
                                },
                                showSpeedCard = showSpeedCard,
                                onShowSpeedCardChange = {
                                    showSpeedCard = it
                                    prefs.edit().putBoolean(PREF_SHOW_SPEED_CARD, it).apply()
                                    if (it) locationPermissionPrompted = false
                                    applyGpsTrackingState()
                                },
                                splitScreenInfoOnly = splitScreenInfoOnly,
                                onSplitScreenInfoOnlyChange = {
                                    splitScreenInfoOnly = it
                                    prefs.edit().putBoolean(PREF_SPLIT_SCREEN_INFO_ONLY, it).apply()
                                },
                                gaugeEnabled = gaugeEnabled,
                                gaugeSource = gaugeSource,
                                onGaugeEnabledChange = {
                                    gaugeEnabled = it
                                    prefs.edit().putBoolean(PREF_GAUGE_ENABLED, it).apply()
                                },
                                onGaugeSourceChange = {
                                    gaugeSource = it
                                    prefs.edit().putString(PREF_GAUGE_SOURCE, it.name).apply()
                                },
                                gaugeCurrentMax = gaugeCurrentMax,
                                gaugePowerMax = gaugePowerMax,
                                gaugeVoltageMax = gaugeVoltageMax,
                                onGaugeCurrentMaxChange = {
                                    gaugeCurrentMax = parsePositiveFloat(it, 60f)
                                    prefs.edit().putFloat(PREF_GAUGE_CURRENT_MAX, gaugeCurrentMax).apply()
                                },
                                onGaugePowerMaxChange = {
                                    gaugePowerMax = parsePositiveFloat(it, 3000f)
                                    prefs.edit().putFloat(PREF_GAUGE_POWER_MAX, gaugePowerMax).apply()
                                },
                                onGaugeVoltageMaxChange = {
                                    gaugeVoltageMax = parsePositiveFloat(it, 86f)
                                    prefs.edit().putFloat(PREF_GAUGE_VOLTAGE_MAX, gaugeVoltageMax).apply()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (autoReconnectAddress != null && passwordInput.isNotBlank()) {
            setStatus("检测到上次成功连接设备，正在自动重连...")
            requestScanPermissionsAndStart()
        }
        applyGpsTrackingState()
    }

    override fun onStart() {
        super.onStart()
        applyGpsTrackingState()
        if (chartRecording) {
            uiHandler.removeCallbacks(chartRecordTask)
            uiHandler.post(chartRecordTask)
        }
        if (mockChargingSample) {
            uiHandler.removeCallbacks(mockDriveTask)
            uiHandler.post(mockDriveTask)
        }
    }

    override fun onStop() {
        super.onStop()
        stopGpsUpdates()
        uiHandler.removeCallbacks(chartRecordTask)
        uiHandler.removeCallbacks(mockDriveTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        disconnectGatt()
        stopGpsUpdates()
        uiHandler.removeCallbacks(clearBleFlashTask)
        uiHandler.removeCallbacks(pollRssiTask)
        uiHandler.removeCallbacks(chartRecordTask)
        uiHandler.removeCallbacks(mockDriveTask)
        uiHandler.removeCallbacks(bleDataWatchdogTask)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestScanPermissionsAndStart() {
        if (hasAllRequiredPermissions()) ensureBluetoothEnabledAndScan() else permissionLauncher.launch(requiredPermissions())
    }

    private fun ensureBluetoothEnabledAndScan() {
        if (isBluetoothEnabled()) startScan() else enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun isBluetoothEnabled(): Boolean = bluetoothAdapter.isEnabled

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!hasAllRequiredPermissions()) {
            setStatus("缺少蓝牙权限，无法扫描。")
            return
        }
        if (!isBluetoothEnabled()) {
            setStatus("蓝牙未开启。")
            return
        }
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        val scanner = bleScanner ?: run {
            setStatus("当前设备不支持 BLE 扫描。")
            return
        }
        deviceItems.clear()
        devicesUi = emptyList()
        selectedDevice = null
        if (!bleConnected) selectedAddress = null
        scanner.startScan(scanCallback)
        scanning = true
        scanButtonText = "停止扫描"
        setStatus("正在扫描 BLE 设备...（若列表为空，请确认系统定位开关已开启）")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        uiHandler.removeCallbacks(refreshDevicesTask)
        refreshScheduled = false
        if (!scanning) return
        bleScanner?.stopScan(scanCallback)
        scanning = false
        scanButtonText = "开始扫描"
        setStatus("扫描已停止。")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val rawName = (result.scanRecord?.deviceName ?: device.name)?.trim().orEmpty()
            if (rawName.isBlank()) return
            if (connectedAddress.equals(address, ignoreCase = true) || selectedAddress.equals(address, ignoreCase = true)) {
                bleSignalRssi = result.rssi
            }
            val hasJkService = hasJkServiceUuid(result)
            val item = deviceItems.find { it.device.address == address }
            if (item == null) {
                deviceItems.add(BleDeviceItem(device, result.rssi, rawName, hasJkService))
            } else {
                item.rssi = result.rssi
                item.name = rawName
                item.hasJkService = item.hasJkService || hasJkService
            }
            val autoTarget = autoReconnectAddress
            if (!autoTarget.isNullOrBlank() && autoTarget.equals(address, ignoreCase = true) && !autoConnecting && gatt == null) {
                selectedDevice = device
                selectedAddress = address
                autoConnecting = true
                stopScan()
                setStatus("发现上次设备，正在自动连接...")
                connectToDevice(device)
                return
            }
            scheduleDeviceListRefresh()
        }

        override fun onScanFailed(errorCode: Int) {
            setStatus("扫描失败: $errorCode")
            stopScan()
        }
    }

    private fun refreshDeviceList() {
        devicesUi = deviceItems
            .filter { it.name.isNotBlank() }
            .sortedWith(
                compareByDescending<BleDeviceItem> { it.hasJkService }
                    .thenByDescending { it.rssi }
            )
            .map { BleDeviceUi(it.device.address ?: "Unknown", it.name, it.rssi, it.hasJkService) }
    }

    private fun hasJkServiceUuid(result: ScanResult): Boolean {
        val uuids = result.scanRecord?.serviceUuids ?: return false
        return uuids.any { parcelUuid ->
            val text = parcelUuid.uuid.toString().lowercase(Locale.US)
            text == "0000ffe0-0000-1000-8000-00805f9b34fb" || text.endsWith("ffe0-0000-1000-8000-00805f9b34fb")
        }
    }

    private fun scheduleDeviceListRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        uiHandler.postDelayed(refreshDevicesTask, 300)
    }

    private fun startBleWatchdog() {
        uiHandler.removeCallbacks(bleDataWatchdogTask)
        lastBleDataAtMs = System.currentTimeMillis()
        uiHandler.postDelayed(bleDataWatchdogTask, BLE_WATCHDOG_INTERVAL_MS)
    }

    private fun stopBleWatchdog() {
        uiHandler.removeCallbacks(bleDataWatchdogTask)
    }

    @SuppressLint("MissingPermission")
    private fun triggerBleAutoReconnect() {
        if (!hasAllRequiredPermissions()) return
        val address = connectedAddress ?: gatt?.device?.address ?: autoReconnectAddress ?: return
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return
        val device = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            return
        }
        bleAutoReconnecting = true
        autoConnecting = true
        setStatus("3秒未收到数据且信号正常，自动重连中...")
        connectToDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasAllRequiredPermissions()) {
            setStatus("缺少蓝牙权限，无法连接。")
            autoConnecting = false
            return
        }
        if (mockChargingSample) {
            stopMockDriving()
        }
        disconnectGatt()
        authenticated = false
        frameBuffer.clear()
        latestMetrics = null
        applyGpsTrackingState()
        bleConnected = false
        bleActivityFlash = false
        bleSignalRssi = deviceItems.firstOrNull { it.device.address == device.address }?.rssi
        connectedAddress = device.address
        lastBleDataAtMs = System.currentTimeMillis()
        setStatus("正在连接 ${formatDevice(device)} ...")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(this, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        uiHandler.removeCallbacks(pollTask)
        uiHandler.removeCallbacks(pollRssiTask)
        uiHandler.removeCallbacks(clearBleFlashTask)
        stopBleWatchdog()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        latestMetrics = null
        applyGpsTrackingState()
        bleConnected = false
        bleSignalRssi = null
        bleActivityFlash = false
        connectedAddress = null
        lastBleDataAtMs = 0L
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                bleAutoReconnecting = false
                bleConnected = true
                connectedAddress = gatt.device.address
                uiHandler.removeCallbacks(pollRssiTask)
                uiHandler.post(pollRssiTask)
                setStatus("已连接，正在发现服务...")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                setStatus("已断开连接。")
                bleAutoReconnecting = false
                authenticated = false
                autoConnecting = false
                uiHandler.removeCallbacks(pollTask)
                uiHandler.removeCallbacks(pollRssiTask)
                uiHandler.removeCallbacks(clearBleFlashTask)
                stopBleWatchdog()
                latestMetrics = null
                applyGpsTrackingState()
                bleConnected = false
                bleSignalRssi = null
                bleActivityFlash = false
                connectedAddress = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setStatus("服务发现失败: $status")
                return
            }
            val service = gatt.services.firstOrNull { it.uuid == JK_SERVICE_UUID }
            if (service == null) {
                setStatus("未找到 JK 服务 FFE0。")
                return
            }
            val chars = service.characteristics.filter { it.uuid == JK_CHARACTERISTIC_UUID }
            if (chars.isEmpty()) {
                setStatus("未找到 JK 特征 FFE1。")
                return
            }
            writeCharacteristic = chars.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            }
            notifyCharacteristic = chars.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            } ?: writeCharacteristic
            if (writeCharacteristic == null || notifyCharacteristic == null) {
                setStatus("FFE1 特征不支持读写通知组合。")
                return
            }
            enableNotifications(gatt, notifyCharacteristic!!)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setStatus("通知已开启，正在读取设备信息...")
                sendCommand(COMMAND_DEVICE_INFO)
            } else {
                setStatus("通知开启失败: $status")
                autoConnecting = false
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onNotifyBytes(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onNotifyBytes(value)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bleSignalRssi = rssi
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
        if (cccd == null) {
            setStatus("未找到 CCCD 描述符，无法订阅通知。")
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun onNotifyBytes(chunk: ByteArray) {
        markBleActivity()
        lastBleDataAtMs = System.currentTimeMillis()
        if (chunk.size >= 4 && chunk[0] == 0x55.toByte() && chunk[1] == 0xAA.toByte() &&
            chunk[2] == 0xEB.toByte() && chunk[3] == 0x90.toByte()
        ) {
            frameBuffer.clear()
        }
        for (b in chunk) frameBuffer.add(b)

        if (frameBuffer.size >= MIN_FRAME_SIZE) {
            val frame = frameBuffer.take(MIN_FRAME_SIZE).toByteArray()
            frameBuffer.clear()
            if (!validateCrc(frame)) {
                setStatus("收到数据但 CRC 校验失败。")
                return
            }
            handleFrame(frame)
        } else if (frameBuffer.size > MAX_FRAME_SIZE) {
            frameBuffer.clear()
        }
    }

    private fun handleFrame(frame: ByteArray) {
        when (frame[4].toInt() and 0xFF) {
            FRAME_TYPE_DEVICE_INFO -> {
                val deviceName = parseCString(frame, 46, 16)
                val devicePasscode = parseCString(frame, 62, 16)
                val typedPassword = passwordInput.trim()
                authenticated = typedPassword.isNotEmpty() && typedPassword == devicePasscode
                if (authenticated) {
                    setStatus("密码校验通过。设备: $deviceName，开始读取状态数据...")
                    autoConnecting = false
                    bleAutoReconnecting = false
                    val connectedAddress = gatt?.device?.address ?: selectedDevice?.address
                    if (!connectedAddress.isNullOrBlank()) {
                        autoReconnectAddress = connectedAddress
                        prefs.edit()
                            .putString(PREF_LAST_SUCCESS_DEVICE_ADDRESS, connectedAddress)
                            .putString(PREF_LAST_SUCCESS_PASSWORD, typedPassword)
                            .putString(PREF_LAST_PASSWORD, typedPassword)
                            .apply()
                    }
                    sendCommand(COMMAND_CELL_INFO)
                    uiHandler.removeCallbacks(pollTask)
                    uiHandler.postDelayed(pollTask, 3000)
                    startBleWatchdog()
                } else {
                    autoConnecting = false
                    stopBleWatchdog()
                    setStatus("密码校验失败。设备密码不匹配，无法读取状态数据。")
                }
            }

            FRAME_TYPE_CELL_INFO -> {
                if (!authenticated) return
                val parsed = parseCellInfo(frame)
                latestMetrics = parsed
                applyGpsTrackingState()
                setStatus("已更新电池数据。")
            }

            FRAME_TYPE_SETTINGS -> if (authenticated) setStatus("收到设置帧。")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(command: Int) {
        val gatt = gatt ?: return
        val characteristic = writeCharacteristic ?: return
        val frame = ByteArray(20)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0xEB.toByte()
        frame[4] = (command and 0xFF).toByte()
        frame[5] = 0x00
        frame[16] = currentFrameCounter
        currentFrameCounter = (currentFrameCounter + 1).toByte()
        frame[19] = sum8(frame, 19)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = frame
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        markBleActivity()
    }

    private fun markBleActivity() {
        bleActivityFlash = true
        uiHandler.removeCallbacks(clearBleFlashTask)
        uiHandler.postDelayed(clearBleFlashTask, 260)
    }

    private fun parseCellInfo(frame: ByteArray): ParsedMetrics {
        val enabledMask = getUInt32LE(frame, 70)
        val cellVoltages = FloatArray(32) { i -> getUInt16LE(frame, 6 + i * 2) / 1000f }
        val enabledIndices = mutableListOf<Int>()
        for (i in 0 until 32) {
            val enabled = if (enabledMask != 0L) {
                ((enabledMask shr i) and 0x01L) == 0x01L
            } else {
                cellVoltages[i] > 0f
            }
            if (enabled) enabledIndices.add(i)
        }

        var minCellVoltage = 0f
        var maxCellVoltage = 0f
        var minCellIndex = 0
        var maxCellIndex = 0
        var avgCellVoltage = 0f
        if (enabledIndices.isNotEmpty()) {
            var sum = 0f
            minCellVoltage = Float.MAX_VALUE
            maxCellVoltage = 0f
            for (idx in enabledIndices) {
                val v = cellVoltages[idx]
                sum += v
                if (v < minCellVoltage) {
                    minCellVoltage = v
                    minCellIndex = idx + 1
                }
                if (v > maxCellVoltage) {
                    maxCellVoltage = v
                    maxCellIndex = idx + 1
                }
            }
            avgCellVoltage = sum / enabledIndices.size
        }

        val minCellFromFrame = frame[79].toInt() and 0xFF
        val maxCellFromFrame = frame[78].toInt() and 0xFF
        if (minCellFromFrame > 0) minCellIndex = minCellFromFrame
        if (maxCellFromFrame > 0) maxCellIndex = maxCellFromFrame
        val deltaCellVoltage = if (enabledIndices.isNotEmpty()) abs(maxCellVoltage - minCellVoltage) else 0f

        val totalVoltage = getUInt32LE(frame, 150) / 1000f
        val current = getInt32LE(frame, 158) / 1000f
        val power = totalVoltage * current
        val soc = frame[173].toInt() and 0xFF
        val soh = frame[190].toInt() and 0xFF
        val mosTemperature = getInt16LE(frame, 144) / 10f
        val temp1 = getInt16LE(frame, 162) / 10f
        val temp2 = getInt16LE(frame, 164) / 10f
        val temp3 = getInt16LE(frame, 258) / 10f
        val temp4 = getInt16LE(frame, 256) / 10f
        val temp5 = getInt16LE(frame, 254) / 10f
        val errorsBitmask = getUInt32LE(frame, 166)
        val balanceCurrent = getInt16LE(frame, 170) / 1000f
        val capacityRemainingAh = getUInt32LE(frame, 174) / 1000f
        val fullChargeCapacityAh = getUInt32LE(frame, 178) / 1000f
        val cycleCount = getUInt32LE(frame, 182)
        val totalCycleCapacityAh = getUInt32LE(frame, 186) / 1000f
        val totalRuntimeSeconds = getUInt32LE(frame, 194)
        val chargingMosEnabled = (frame[198].toInt() and 0xFF) == 1
        val dischargingMosEnabled = (frame[199].toInt() and 0xFF) == 1
        val prechargingEnabled = (frame[200].toInt() and 0xFF) == 1
        val balancingEnabled = (frame[201].toInt() and 0xFF) == 1
        val heatingEnabled = (frame[215].toInt() and 0xFF) == 1
        val emergencyCountdownSeconds = getUInt16LE(frame, 218)
        val chargeStatusId = frame[280].toInt() and 0xFF

        return ParsedMetrics(
            totalVoltage = totalVoltage,
            current = current,
            power = power,
            soc = soc,
            soh = soh,
            mosTemperature = mosTemperature,
            temp1 = temp1,
            temp2 = temp2,
            temp3 = temp3,
            temp4 = temp4,
            temp5 = temp5,
            balanceCurrent = balanceCurrent,
            capacityRemainingAh = capacityRemainingAh,
            fullChargeCapacityAh = fullChargeCapacityAh,
            cycleCount = cycleCount,
            totalCycleCapacityAh = totalCycleCapacityAh,
            totalRuntimeSeconds = totalRuntimeSeconds,
            emergencyCountdownSeconds = emergencyCountdownSeconds,
            chargeStatusId = chargeStatusId,
            chargingMosEnabled = chargingMosEnabled,
            dischargingMosEnabled = dischargingMosEnabled,
            prechargingEnabled = prechargingEnabled,
            balancingEnabled = balancingEnabled,
            heatingEnabled = heatingEnabled,
            enabledCellCount = enabledIndices.size,
            minCellVoltage = minCellVoltage,
            maxCellVoltage = maxCellVoltage,
            avgCellVoltage = avgCellVoltage,
            deltaCellVoltage = deltaCellVoltage,
            minCellIndex = minCellIndex,
            maxCellIndex = maxCellIndex,
            errorsBitmask = errorsBitmask,
            cellVoltages = cellVoltages.toList()
        )
    }

    private fun parseCString(data: ByteArray, start: Int, len: Int): String {
        val end = (start + len).coerceAtMost(data.size)
        val raw = data.copyOfRange(start, end)
        val zero = raw.indexOf(0)
        val valid = if (zero >= 0) raw.copyOfRange(0, zero) else raw
        return valid.toString(Charsets.UTF_8).trim()
    }

    private fun validateCrc(frame: ByteArray): Boolean {
        return frame[299] == sum8(frame, 299)
    }

    private fun sum8(data: ByteArray, len: Int): Byte {
        var crc = 0
        for (i in 0 until len) crc = (crc + (data[i].toInt() and 0xFF)) and 0xFF
        return crc.toByte()
    }

    private fun getUInt32LE(data: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun getInt32LE(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun getUInt16LE(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun getInt16LE(data: ByteArray, offset: Int): Short {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short
    }

    private fun formatDevice(device: BluetoothDevice): String {
        val name = device.name ?: "Unknown"
        return "$name (${device.address})"
    }

    private fun setStatus(message: String) {
        runOnUiThread { statusText = message }
    }

    private fun parseGaugeSource(raw: String?): GaugeMetricSource {
        if (raw.isNullOrBlank()) return GaugeMetricSource.POWER
        return GaugeMetricSource.entries.firstOrNull { it.name == raw } ?: GaugeMetricSource.POWER
    }

    private fun parseThemeMode(raw: String?): ThemeMode {
        if (raw.isNullOrBlank()) return ThemeMode.DARK
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    private fun parsePositiveFloat(value: Float, fallback: Float): Float {
        return if (value.isFinite() && value > 0f) value else fallback
    }

    private fun startChartRecording() {
        if (chartPlayback) {
            chartPlayback = false
        }
        chartSamples = emptyList()
        chartRecording = true
        uiHandler.removeCallbacks(chartRecordTask)
        uiHandler.post(chartRecordTask)
        setStatus("图表录制已开始。")
    }

    private fun stopChartRecording(silent: Boolean = false) {
        chartRecording = false
        uiHandler.removeCallbacks(chartRecordTask)
        if (!silent) {
            setStatus("图表录制已停止，可回显最近一分钟数据。")
        }
    }

    private fun appendTrendSample(sample: TrendSample) {
        chartSamples = chartSamples + sample
    }

    private fun startMockDriving() {
        mockChargingSample = true
        mockDriveTick = 0
        lastMockPhase = null
        mockDriveSoc = 23.0f
        mockRuntimeSeconds = latestMetrics?.totalRuntimeSeconds ?: 2_963_445L
        mockGpsSpeedKmhValue = 0f
        if (!chartRecording) {
            startChartRecording()
        }
        val first = buildMockDrivingMetrics(mockDriveTick++)
        latestMetrics = first
        updateMockGpsState(0)
        uiHandler.removeCallbacks(mockDriveTask)
        uiHandler.post(mockDriveTask)
        showToast("MOCK行车已启动")
        setStatus("已启动 MOCK 行车数据。")
    }

    private fun stopMockDriving() {
        mockChargingSample = false
        lastMockPhase = null
        uiHandler.removeCallbacks(mockDriveTask)
        gpsSignalState = GpsSignalState.OFF
        gpsSpeedKmh = null
        showToast("MOCK行车已停止")
        setStatus("已停止 MOCK 行车数据。")
    }

    private fun mockPhaseName(cycle: Int): String {
        return when (cycle) {
            in 0..24 -> "急加速"
            in 25..78 -> "高速巡航"
            in 79..95 -> "急减速回收"
            in 96..128 -> "城区变速"
            in 129..150 -> "停车等待"
            else -> "再次重踩"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateMockGpsState(tick: Int) {
        if (!showSpeedCard || !gpsEnabled) {
            gpsSignalState = GpsSignalState.OFF
            gpsSpeedKmh = null
            return
        }
        val target = when (tick % 180) {
            in 0..24 -> 12f + (tick % 25) * 2.1f
            in 25..78 -> 62f + (sin(tick * 0.22f) * 7f).toFloat()
            in 79..95 -> (45f - (tick - 79) * 2.2f).coerceAtLeast(6f)
            in 96..128 -> 20f + (sin(tick * 0.35f) * 4f).toFloat()
            in 129..150 -> if (tick % 7 < 3) 0f else 4f
            else -> 35f + (sin(tick * 0.29f) * 6f).toFloat()
        }.coerceAtLeast(0f)
        mockGpsSpeedKmhValue += (target - mockGpsSpeedKmhValue) * 0.36f
        gpsSpeedKmh = mockGpsSpeedKmhValue
        gpsSignalState = GpsSignalState.READY
    }

    private fun buildMockDrivingMetrics(tick: Int): ParsedMetrics {
        val cycle = tick % 180
        val current = when (cycle) {
            in 0..24 -> -(14f + cycle * 2.15f) // 急加速拉到约 65A
            in 25..78 -> -(56f + (sin(cycle * 0.19f) * 11.5f).toFloat()) // 高负载巡航
            in 79..95 -> (14f + ((cycle - 79) * 2.25f)) // 强力回收到约 50A
            in 96..128 -> -(24f + (sin(cycle * 0.33f) * 8.2f).toFloat()) // 城区波动
            in 129..150 -> (if (cycle % 7 < 2) 0f else -1.5f) // 红灯等待
            else -> -(42f + (sin(cycle * 0.41f) * 10f).toFloat()) // 再次重踩
        }

        // 20S ternary pack, nominal around 72V under mixed load.
        val totalVoltage = (73.2f - (abs(current) * 0.020f) + (if (current > 0f) 0.35f else 0f) +
            (sin(tick * 0.06f) * 0.12f).toFloat()).coerceIn(71.0f, 75.8f)
        val power = totalVoltage * current

        val fullChargeAh = 99.82f
        val deltaSoc = when {
            current < -1f -> -abs(current) * 0.00018f
            current > 1f -> current * 0.00012f
            else -> -0.0002f
        }
        mockDriveSoc = (mockDriveSoc + deltaSoc).coerceIn(5f, 99.5f)
        val remainingAh = (fullChargeAh * (mockDriveSoc / 100f)).coerceAtLeast(0f)
        mockRuntimeSeconds += 1

        val seriesCount = 20
        val baseCell = (totalVoltage / seriesCount).coerceIn(3.35f, 3.95f)
        val cellVoltages = (0 until seriesCount).map { idx ->
            val ripple = (sin((tick * 0.12f) + (idx * 0.65f)) * 0.0036f).toFloat()
            (baseCell + ripple).coerceIn(3.30f, 4.05f)
        }
        val minV = cellVoltages.minOrNull() ?: baseCell
        val maxV = cellVoltages.maxOrNull() ?: baseCell
        val minIdx = cellVoltages.indexOf(minV) + 1
        val maxIdx = cellVoltages.indexOf(maxV) + 1
        val avgV = cellVoltages.sum() / cellVoltages.size

        val temp1 = (34f + (abs(current) * 0.09f) + (sin(tick * 0.04f) * 1.2f).toFloat()).coerceIn(28f, 57f)
        val temp2 = (temp1 + 0.8f + (sin(tick * 0.07f) * 0.8f).toFloat()).coerceIn(28f, 58f)
        val mosTemp = (38f + (abs(current) * 0.14f) + (sin(tick * 0.05f) * 1.4f).toFloat()).coerceIn(32f, 72f)

        return ParsedMetrics(
            totalVoltage = totalVoltage,
            current = current,
            power = power,
            soc = mockDriveSoc.toInt(),
            soh = 99,
            mosTemperature = mosTemp,
            temp1 = temp1,
            temp2 = temp2,
            temp3 = -200f,
            temp4 = -200f,
            temp5 = ((temp1 + temp2) * 0.5f).coerceIn(30f, 60f),
            balanceCurrent = 0f,
            capacityRemainingAh = remainingAh,
            fullChargeCapacityAh = fullChargeAh,
            cycleCount = 128,
            totalCycleCapacityAh = 8240.000f,
            totalRuntimeSeconds = mockRuntimeSeconds,
            emergencyCountdownSeconds = 0,
            chargeStatusId = if (current > 0f) 2 else 0,
            chargingMosEnabled = true,
            dischargingMosEnabled = true,
            prechargingEnabled = false,
            balancingEnabled = abs(maxV - minV) > 0.004f,
            heatingEnabled = false,
            enabledCellCount = cellVoltages.size,
            minCellVoltage = minV,
            maxCellVoltage = maxV,
            avgCellVoltage = avgV,
            deltaCellVoltage = abs(maxV - minV),
            minCellIndex = minIdx,
            maxCellIndex = maxIdx,
            errorsBitmask = 0L,
            cellVoltages = cellVoltages
        )
    }

    private fun applySystemBarAppearance() {
        val darkMode = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkMode
        insetsController.isAppearanceLightNavigationBars = !darkMode
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun applyGpsTrackingState() {
        if (!showSpeedCard || !gpsEnabled) {
            stopGpsUpdates()
            gpsSignalState = GpsSignalState.OFF
            gpsSpeedKmh = null
            return
        }
        if (mockChargingSample) {
            stopGpsUpdates()
            return
        }
        startGpsUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        if (gpsUpdating) return
        if (!hasFineLocationPermission()) {
            if (!locationPermissionPrompted) {
                locationPermissionPrompted = true
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                gpsSignalState = GpsSignalState.OFF
                gpsSpeedKmh = null
            }
            return
        }
        gpsUpdating = true
        lastGpsUpdateAtMs = System.currentTimeMillis()
        gpsSignalState = GpsSignalState.SEARCHING
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener)
        uiHandler.removeCallbacks(gpsTimeoutTask)
        uiHandler.postDelayed(gpsTimeoutTask, 3000)
    }

    @SuppressLint("MissingPermission")
    private fun stopGpsUpdates() {
        if (!gpsUpdating) return
        gpsUpdating = false
        locationManager.removeUpdates(gpsListener)
        uiHandler.removeCallbacks(gpsTimeoutTask)
    }

    companion object {
        private const val PREFS_NAME = "jkbms_prefs"
        private const val PREF_LAST_PASSWORD = "pref_last_password"
        private const val PREF_LAST_SUCCESS_PASSWORD = "pref_last_success_password"
        private const val PREF_LAST_SUCCESS_DEVICE_ADDRESS = "pref_last_success_device_address"
        private const val PREF_USE_APP_FONT = "pref_use_app_font"
        private const val PREF_THEME_MODE = "pref_theme_mode"
        private const val PREF_SHOW_SPEED_CARD = "pref_show_speed_card"
        private const val PREF_SPLIT_SCREEN_INFO_ONLY = "pref_split_screen_info_only"
        private const val PREF_GAUGE_ENABLED = "pref_gauge_enabled"
        private const val PREF_GAUGE_SOURCE = "pref_gauge_source"
        private const val PREF_GAUGE_CURRENT_MAX = "pref_gauge_current_max"
        private const val PREF_GAUGE_POWER_MAX = "pref_gauge_power_max"
        private const val PREF_GAUGE_VOLTAGE_MAX = "pref_gauge_voltage_max"

        private const val MIN_FRAME_SIZE = 300
        private const val MAX_FRAME_SIZE = 420

        private const val FRAME_TYPE_SETTINGS = 0x01
        private const val FRAME_TYPE_CELL_INFO = 0x02
        private const val FRAME_TYPE_DEVICE_INFO = 0x03

        private const val COMMAND_CELL_INFO = 0x96
        private const val COMMAND_DEVICE_INFO = 0x97
        private const val PREF_GPS_ENABLED = "pref_gps_enabled"
        private const val BLE_DATA_TIMEOUT_MS = 3_000L
        private const val BLE_WATCHDOG_INTERVAL_MS = 1_000L
        private const val BLE_RECONNECT_RSSI_MIN_DBM = -90

        private val JK_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val JK_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    }
}
