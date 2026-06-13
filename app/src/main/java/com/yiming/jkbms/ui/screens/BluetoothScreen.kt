package com.yiming.jkbms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yiming.jkbms.model.BleDeviceUi

@Composable
fun BluetoothPage(
    modifier: Modifier = Modifier,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    scanButtonText: String,
    devicesUi: List<BleDeviceUi>,
    selectedAddress: String?,
    statusText: String,
    onToggleScan: () -> Unit,
    onConnect: () -> Unit,
    onSelectDevice: (BleDeviceUi) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bluetooth,
                        contentDescription = "BLE",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("蓝牙连接", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "进入页面自动扫描，选择设备后连接读取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Text("扫描到的蓝牙设备", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(devicesUi, key = { it.address }) { item ->
                    val isSelected = selectedAddress == item.address
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(item) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                if (item.hasJkService) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF448AFF), CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("JK", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(
                                item.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RssiSignalIndicator(item.rssi)
                            Text(
                                "${item.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            label = { Text("输入 BMS 密码（用于校验）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onToggleScan, modifier = Modifier.weight(1f)) {
                Text(scanButtonText)
            }
            Button(onClick = onConnect, modifier = Modifier.weight(1f)) {
                Text("连接并读取")
            }
        }

        Text("状态", style = MaterialTheme.typography.titleMedium)
        Text(statusText)
    }
}

@Composable
private fun RssiSignalIndicator(rssi: Int, modifier: Modifier = Modifier) {
    val level = when {
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        rssi >= -95 -> 1
        else -> 0
    }
    val activeColor = when (level) {
        4 -> Color(0xFF4CAF50)
        3 -> Color(0xFF66BB6A)
        2 -> Color(0xFFFFC107)
        1 -> Color(0xFFFF9800)
        else -> Color(0xFFEF5350)
    }
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val barHeight = (i * 3 + 2).dp
            val color = if (i <= level) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = barHeight)
                    .background(color = color, shape = RoundedCornerShape(1.dp))
            )
        }
    }
}
