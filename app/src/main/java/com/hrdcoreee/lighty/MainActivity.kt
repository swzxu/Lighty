package com.hrdcoreee.lighty

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrdcoreee.lighty.ble.ConnectionState
import com.hrdcoreee.lighty.ui.components.UpdateDialog
import com.hrdcoreee.lighty.update.UpdateEvent
import com.hrdcoreee.lighty.i18n.LocalStrings
import com.hrdcoreee.lighty.i18n.stringsFor
import com.hrdcoreee.lighty.ui.screens.ControlScreen
import com.hrdcoreee.lighty.ui.screens.ScanScreen
import com.hrdcoreee.lighty.ui.screens.SettingsScreen
import com.hrdcoreee.lighty.ui.theme.LightyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            LightyTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LightyApp(viewModel)
                }
            }
        }
    }
}

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private enum class Screen { SCAN, SETTINGS, CONTROL }

@Composable
private fun LightyApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val strings = stringsFor(language)

    CompositionLocalProvider(LocalStrings provides strings) {
        var hasPermissions by remember {
            mutableStateOf(
                requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result -> hasPermissions = result.values.all { it } }

        val enableBtLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* state is re-read on scan */ }

        LaunchedEffect(Unit) {
            if (!hasPermissions) permissionLauncher.launch(requiredPermissions)
        }

        if (!hasPermissions) {
            PermissionScreen(onRequest = { permissionLauncher.launch(requiredPermissions) })
            return@CompositionLocalProvider
        }

        val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
        val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
        val boundDevice by viewModel.boundDevice.collectAsStateWithLifecycle()
        val devices by viewModel.devices.collectAsStateWithLifecycle()
        val scanning by viewModel.scanning.collectAsStateWithLifecycle()
        val showAllDevices by viewModel.showAllDevices.collectAsStateWithLifecycle()
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val isOn by viewModel.isOn.collectAsStateWithLifecycle()
        val color by viewModel.color.collectAsStateWithLifecycle()
        val hue by viewModel.hue.collectAsStateWithLifecycle()
        val saturation by viewModel.saturation.collectAsStateWithLifecycle()
        val value by viewModel.value.collectAsStateWithLifecycle()
        val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
        val checkingUpdate by viewModel.checkingUpdate.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

        var showSettings by remember { mutableStateOf(false) }

        // Intercept system Back when the Settings overlay is open so the
        // user returns to the previous screen instead of exiting the app.
        BackHandler(enabled = showSettings) { showSettings = false }

        // Only scan when no strip is bound; a bound strip auto-connects instead.
        LaunchedEffect(boundDevice == null) {
            if (boundDevice == null && viewModel.isBluetoothEnabled()) viewModel.startScan()
        }

        // A failed manual connection is worth a toast; a bound strip shows "offline" instead.
        LaunchedEffect(connectionState) {
            if (connectionState == ConnectionState.FAILED && boundDevice == null) {
                Toast.makeText(context, strings.connectFailed, Toast.LENGTH_SHORT).show()
            }
        }

        // One-shot update outcomes (up-to-date / failures) as toasts.
        LaunchedEffect(Unit) {
            viewModel.updateEvents.collect { event ->
                val message = when (event) {
                    UpdateEvent.UP_TO_DATE -> strings.upToDate
                    UpdateEvent.CHECK_FAILED -> strings.updateCheckFailed
                    UpdateEvent.DOWNLOAD_FAILED -> strings.downloadFailed
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        updateInfo?.let { info ->
            UpdateDialog(
                versionName = info.versionName,
                releaseNotes = info.releaseNotes,
                downloadProgress = downloadProgress,
                onUpdate = viewModel::downloadAndInstallUpdate,
                onDismiss = viewModel::dismissUpdate,
            )
        }

        val online = connectionState == ConnectionState.CONNECTED
        val connecting = connectionState == ConnectionState.CONNECTING
        val screen = when {
            boundDevice != null && !showSettings -> Screen.CONTROL
            showSettings -> Screen.SETTINGS
            else -> Screen.SCAN
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
            },
            label = "screen"
        ) { target ->
            when (target) {
                Screen.CONTROL -> ControlScreen(
                    deviceName = boundDevice?.name ?: boundDevice?.address ?: strings.deviceFallback,
                    online = online,
                    connecting = connecting,
                    isOn = isOn,
                    color = color,
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSetPower = viewModel::setPower,
                    onHueChange = viewModel::onHueChange,
                    onSaturationValueChange = viewModel::onSaturationValueChange,
                    onBrightnessChange = viewModel::onBrightnessChange,
                    onPreset = viewModel::applyColor,
                    onOpenSettings = { showSettings = true },
                )

                Screen.SETTINGS -> SettingsScreen(
                    language = language,
                    themeMode = themeMode,
                    showAllDevices = showAllDevices,
                    boundDeviceName = boundDevice?.name ?: boundDevice?.address,
                    onBack = { showSettings = false },
                    onLanguageChange = viewModel::setLanguage,
                    onThemeChange = viewModel::setThemeMode,
                    onShowAllChange = viewModel::setShowAllDevices,
                    onUnbind = {
                        showSettings = false
                        viewModel.unbind()
                    },
                    checkingUpdate = checkingUpdate,
                    onCheckUpdates = { viewModel.checkForUpdates(silent = false) },
                )

                Screen.SCAN -> ScanScreen(
                    devices = devices,
                    scanning = scanning,
                    bluetoothEnabled = viewModel.isBluetoothEnabled(),
                    connectionState = connectionState,
                    connectingAddress = connectedDevice?.address,
                    onToggleScan = viewModel::toggleScan,
                    onConnect = viewModel::bindAndConnect,
                    onEnableBluetooth = {
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(112.dp)
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
                    Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                s.permTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                s.permMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRequest) { Text(s.allow) }
        }
    }
}
