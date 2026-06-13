package com.yiming.jkbms.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yiming.jkbms.model.GaugeMetricSource
import com.yiming.jkbms.model.ThemeMode

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    useAppFont: Boolean,
    onFontToggle: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    showSpeedCard: Boolean,
    onShowSpeedCardChange: (Boolean) -> Unit,
    splitScreenInfoOnly: Boolean,
    onSplitScreenInfoOnlyChange: (Boolean) -> Unit,
    gaugeEnabled: Boolean,
    gaugeSource: GaugeMetricSource,
    onGaugeEnabledChange: (Boolean) -> Unit,
    onGaugeSourceChange: (GaugeMetricSource) -> Unit,
    gaugeCurrentMax: Float,
    gaugePowerMax: Float,
    gaugeVoltageMax: Float,
    onGaugeCurrentMaxChange: (Float) -> Unit,
    onGaugePowerMaxChange: (Float) -> Unit,
    onGaugeVoltageMaxChange: (Float) -> Unit
) {
    val currentMaxTextState = remember { mutableStateOf(gaugeCurrentMax.toString()) }
    val powerMaxTextState = remember { mutableStateOf(gaugePowerMax.toString()) }
    val voltageMaxTextState = remember { mutableStateOf(gaugeVoltageMax.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("软件字体", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "开启：Consolas Bold；关闭：系统默认字体",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                Switch(checked = useAppFont, onCheckedChange = onFontToggle)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("主题", style = MaterialTheme.typography.titleSmall)
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == themeMode,
                            onClick = { onThemeModeChange(mode) }
                        )
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("显示速度卡片", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "信息页顶部右侧显示 GPS 速度方块",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                Switch(checked = showSpeedCard, onCheckedChange = onShowSpeedCardChange)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("分屏信息", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "小窗/分屏时仅显示板上指标，并隐藏底部导航",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
                Switch(checked = splitScreenInfoOnly, onCheckedChange = onSplitScreenInfoOnlyChange)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("顶部仪表盘", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "开启后在信息页顶部显示仪表盘 + 3项小卡片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                    Switch(checked = gaugeEnabled, onCheckedChange = onGaugeEnabledChange)
                }
                if (gaugeEnabled) {
                    Text("仪表数据源", style = MaterialTheme.typography.labelLarge)
                    GaugeMetricSource.entries.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = source == gaugeSource,
                                onClick = { onGaugeSourceChange(source) }
                            )
                            Text(source.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("仪表量程", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = currentMaxTextState.value,
                            onValueChange = {
                                currentMaxTextState.value = it
                                it.toFloatOrNull()?.takeIf { v -> v > 0f }?.let(onGaugeCurrentMaxChange)
                            },
                            label = { Text("电流上限(A)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = powerMaxTextState.value,
                            onValueChange = {
                                powerMaxTextState.value = it
                                it.toFloatOrNull()?.takeIf { v -> v > 0f }?.let(onGaugePowerMaxChange)
                            },
                            label = { Text("功率上限(W)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = voltageMaxTextState.value,
                        onValueChange = {
                            voltageMaxTextState.value = it
                            it.toFloatOrNull()?.let(onGaugeVoltageMaxChange)
                        },
                        label = { Text("电压上限(V)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "电压仪表按 0~上限 显示，超过上限会触发爆表红色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "settings")
                Text(
                    "蓝牙会记住上次成功连接的设备与密码，下次启动自动重连。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
