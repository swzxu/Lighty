package com.hrdcoreee.lighty.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrdcoreee.lighty.i18n.LocalStrings
import com.hrdcoreee.lighty.ui.components.HsvColorPicker
import kotlin.math.roundToInt

private val PRESETS = listOf(
    Color(0xFFFF0000), // red
    Color(0xFFFF7A00), // orange
    Color(0xFFFFE000), // yellow
    Color(0xFF00FF00), // green
    Color(0xFF00E5FF), // cyan
    Color(0xFF0066FF), // blue
    Color(0xFF7B00FF), // violet
    Color(0xFFFF00E5), // magenta
    Color(0xFFFF4D8D), // pink
    Color(0xFFFFFFFF), // white
    Color(0xFFFFD9A0), // warm white
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    deviceName: String,
    online: Boolean,
    connecting: Boolean,
    isOn: Boolean,
    color: Color,
    hue: Float,
    saturation: Float,
    value: Float,
    onSetPower: (Boolean) -> Unit,
    onHsvChange: (hue: Float, saturation: Float, value: Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreset: (Color) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val statusText = when {
        online -> s.connected
        connecting -> s.connecting
        else -> s.offline
    }
    val statusColor = if (online) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            deviceName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settingsCd)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!online) OfflineCard(connecting = connecting)

            Box {
                Column(
                    modifier = Modifier.alpha(if (online) 1f else 0.4f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PowerHero(isOn = isOn, color = color, onToggle = { onSetPower(!isOn) })

                    PowerSwitchRow(isOn = isOn, onSetPower = onSetPower)

                    SectionCard(title = s.brightness) {
                        BrightnessSlider(value = value, onBrightnessChange = onBrightnessChange)
                    }

                    SectionCard(title = s.quickColors) {
                        PresetGrid(onPreset = onPreset)
                    }

                    SectionCard(title = s.customColor) {
                        HsvColorPicker(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            onColorChange = onHsvChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // When offline, swallow all touches so controls can't be used.
                if (!online) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OfflineCard(connecting: Boolean) {
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
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (connecting) s.connecting else s.offline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(s.offlineHint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PowerHero(isOn: Boolean, color: Color, onToggle: () -> Unit) {
    val s = LocalStrings.current
    val orbColor by animateColorAsState(
        targetValue = if (isOn) color else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(300),
        label = "orb"
    )
    val glowSize by animateDpAsState(
        targetValue = if (isOn) 210.dp else 0.dp,
        animationSpec = tween(400),
        label = "glow"
    )
    val onOrb = if (orbColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft glow (blur is applied on Android 12+, gracefully ignored below).
        Box(
            modifier = Modifier
                .size(glowSize)
                .blur(60.dp)
                .clip(CircleShape)
                .background(if (isOn) color.copy(alpha = 0.55f) else Color.Transparent)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(orbColor)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = onOrb,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isOn) s.onLabel else s.offLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onOrb
                )
            }
        }
    }
}

@Composable
private fun PowerSwitchRow(isOn: Boolean, onSetPower: (Boolean) -> Unit) {
    val s = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.powerLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isOn) s.stripOn else s.stripOff,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isOn, onCheckedChange = onSetPower)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun BrightnessSlider(value: Float, onBrightnessChange: (Float) -> Unit) {
    Column {
        Slider(
            value = value,
            onValueChange = onBrightnessChange,
            valueRange = 0f..1f
        )
        Text(
            "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PresetGrid(onPreset: (Color) -> Unit) {
    // Simple wrap into rows of 6 without extra dependencies.
    val perRow = 6
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PRESETS.chunked(perRow).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowColors.forEach { swatch ->
                    Swatch(color = swatch, onClick = { onPreset(swatch) })
                }
                // Pad the last row so swatches keep their size.
                repeat(perRow - rowColors.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.Swatch(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick)
        )
    }
}
