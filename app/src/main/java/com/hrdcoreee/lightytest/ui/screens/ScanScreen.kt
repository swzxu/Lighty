package com.hrdcoreee.lightytest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrdcoreee.lightytest.ble.ConnectionState
import com.hrdcoreee.lightytest.ble.ScannedDevice
import com.hrdcoreee.lightytest.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    devices: List<ScannedDevice>,
    scanning: Boolean,
    bluetoothEnabled: Boolean,
    connectionState: ConnectionState,
    connectingAddress: String?,
    onToggleScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = {
                    Column {
                        Text("Lighty", fontWeight = FontWeight.Bold)
                        Text(
                            s.scanSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settingsCd)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onToggleScan,
                icon = {
                    if (scanning) Icon(Icons.Rounded.Stop, contentDescription = null)
                    else Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, contentDescription = null)
                },
                text = { Text(if (scanning) s.stopScan else s.scan) },
                containerColor = if (scanning) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (scanning) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AnimatedVisibility(visible = !bluetoothEnabled) {
                    BluetoothOffBanner(onEnableBluetooth)
                }
            }
            item { ScanStatusRow(scanning = scanning, count = devices.size) }

            items(devices, key = { it.address }) { device ->
                val isConnecting = connectionState == ConnectionState.CONNECTING &&
                    connectingAddress == device.address
                DeviceCard(
                    device = device,
                    connecting = isConnecting,
                    onClick = { onConnect(device) }
                )
            }

            if (devices.isEmpty()) {
                item { EmptyState(scanning = scanning) }
            }
        }
    }
}

@Composable
private fun ScanStatusRow(scanning: Boolean, count: Int) {
    val s = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        if (scanning) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val scale by transition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "scale"
            )
            Icon(
                Icons.AutoMirrored.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.scale(scale).size(20.dp)
            )
        } else {
            Icon(
                Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                scanning -> s.searching
                count > 0 -> s.devicesFound(count)
                else -> s.readyToScan
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    connecting: Boolean,
    onClick: () -> Unit,
) {
    val highlighted = device.isElk
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (highlighted) Icons.Rounded.Lightbulb else Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: LocalStrings.current.unknownDevice,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (highlighted) {
                    Spacer(Modifier.height(6.dp))
                    ElkBadge()
                }
            }
            Spacer(Modifier.width(12.dp))
            if (connecting) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp),
                    color = LocalContentColorSafe()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SignalBars(level = device.signalLevel)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${device.rssi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColorSafe().copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = LocalContentColorSafe().copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LocalContentColorSafe(): Color =
    androidx.compose.material3.LocalContentColor.current

@Composable
private fun ElkBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            "ELK-BLEDOM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SignalBars(level: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        val active = MaterialTheme.colorScheme.primary
        val inactive = LocalContentColorSafe().copy(alpha = 0.2f)
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(4.dp)
                    .height((6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) active else inactive)
            )
        }
    }
}

@Composable
private fun BluetoothOffBanner(onEnableBluetooth: () -> Unit) {
    val s = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.BluetoothDisabled, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                s.bluetoothOff,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onEnableBluetooth) { Text(s.enable) }
        }
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (scanning) s.searchingNearby else s.emptyTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (scanning) s.holdCloser else s.emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
