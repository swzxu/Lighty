package com.hrdcoreee.lighty

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrdcoreee.lighty.ble.BleController
import com.hrdcoreee.lighty.ble.BoundDevice
import com.hrdcoreee.lighty.ble.ConnectionState
import com.hrdcoreee.lighty.ble.ElkProtocol
import com.hrdcoreee.lighty.ble.ScannedDevice
import com.hrdcoreee.lighty.i18n.Language
import com.hrdcoreee.lighty.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * UI-facing state holder. Wraps [BleController], persists the strip state and
 * the bound device, and keeps the bound strip alive with a periodic ping.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleController(app)

    private val prefs = app.getSharedPreferences("lighty_settings", Context.MODE_PRIVATE)

    // ---- Settings ---------------------------------------------------------

    private val _language = MutableStateFlow(
        Language.fromCode(prefs.getString(KEY_LANGUAGE, null))
    )
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _showAllDevices = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALL, false))
    val showAllDevices: StateFlow<Boolean> = _showAllDevices.asStateFlow()

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // ---- BLE state --------------------------------------------------------

    val scanning: StateFlow<Boolean> = ble.scanning
    val devices: StateFlow<List<ScannedDevice>> = ble.devices
    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val connectedDevice: StateFlow<ScannedDevice?> = ble.connectedDevice

    private val _boundDevice = MutableStateFlow(readBoundDevice())
    val boundDevice: StateFlow<BoundDevice?> = _boundDevice.asStateFlow()

    // ---- Light state ------------------------------------------------------

    private val _isOn = MutableStateFlow(prefs.getBoolean(KEY_POWER, false))
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    // Color is held as HSV so the picker sliders map cleanly onto the UI.
    private val _hue = MutableStateFlow(prefs.getFloat(KEY_HUE, 0f))          // 0..360
    val hue: StateFlow<Float> = _hue.asStateFlow()

    private val _saturation = MutableStateFlow(prefs.getFloat(KEY_SAT, 1f))    // 0..1
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _value = MutableStateFlow(prefs.getFloat(KEY_VAL, 1f))         // 0..1
    val value: StateFlow<Float> = _value.asStateFlow()

    private val _color = MutableStateFlow(Color.hsv(_hue.value, _saturation.value, _value.value))
    val color: StateFlow<Color> = _color.asStateFlow()

    // Conflated: rapid slider updates collapse to the latest color, keeping BLE calm.
    private val colorSendQueue = Channel<ByteArray>(Channel.CONFLATED)

    private var boundLoop: Job? = null

    init {
        ble.setShowAllDevices(_showAllDevices.value)

        viewModelScope.launch {
            for (frame in colorSendQueue) {
                ble.send(frame)
                persistLight()
                delay(COLOR_SEND_INTERVAL_MS)
            }
        }

        // Push the saved state onto the strip whenever we (re)connect.
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) applySavedStateToDevice()
            }
        }

        // If a strip is already bound, start keeping it alive immediately.
        if (_boundDevice.value != null) startBoundLoop()
    }

    fun isBluetoothEnabled(): Boolean = ble.isBluetoothEnabled()

    fun setLanguage(language: Language) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun setShowAllDevices(show: Boolean) {
        _showAllDevices.value = show
        prefs.edit().putBoolean(KEY_SHOW_ALL, show).apply()
        ble.setShowAllDevices(show)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private fun readThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.AUTO)

    // ---- Scanning ---------------------------------------------------------

    fun toggleScan() = if (scanning.value) ble.stopScan() else ble.startScan()

    fun startScan() = ble.startScan()

    fun stopScan() = ble.stopScan()

    // ---- Binding ----------------------------------------------------------

    /** Connecting to a device from the list binds it as the app's strip. */
    fun bindAndConnect(device: ScannedDevice) {
        val bound = BoundDevice(device.address, device.name)
        _boundDevice.value = bound
        prefs.edit()
            .putString(KEY_BOUND_ADDR, bound.address)
            .putString(KEY_BOUND_NAME, bound.name)
            .apply()
        ble.connect(device)
        startBoundLoop()
    }

    fun unbind() {
        boundLoop?.cancel()
        boundLoop = null
        _boundDevice.value = null
        prefs.edit().remove(KEY_BOUND_ADDR).remove(KEY_BOUND_NAME).apply()
        ble.disconnect()
        ble.startScan()
    }

    private fun startBoundLoop() {
        boundLoop?.cancel()
        boundLoop = viewModelScope.launch {
            while (isActive) {
                val bound = _boundDevice.value ?: break
                when (connectionState.value) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.FAILED -> ble.connect(bound.toScannedDevice())
                    ConnectionState.CONNECTED -> ble.pingRssi()
                    ConnectionState.CONNECTING -> Unit
                }
                delay(PING_INTERVAL_MS)
            }
        }
    }

    // ---- Light control ----------------------------------------------------

    fun togglePower() = setPower(!_isOn.value)

    fun setPower(on: Boolean) {
        _isOn.value = on
        if (on) {
            ble.send(ElkProtocol.POWER_ON)
            sendColorNow()          // restore the chosen color after power-on
        } else {
            ble.send(ElkProtocol.POWER_OFF)
        }
        persistLight()
    }

    fun onHueChange(hue: Float) = updateHsv(hue = hue.coerceIn(0f, 360f))

    fun onSaturationValueChange(saturation: Float, value: Float) =
        updateHsv(saturation = saturation.coerceIn(0f, 1f), value = value.coerceIn(0f, 1f))

    /** Brightness maps directly onto the HSV value channel. */
    fun onBrightnessChange(brightness: Float) =
        updateHsv(value = brightness.coerceIn(0f, 1f))

    fun applyColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        updateHsv(hue = hsv[0], saturation = hsv[1], value = hsv[2])
    }

    private fun updateHsv(
        hue: Float = _hue.value,
        saturation: Float = _saturation.value,
        value: Float = _value.value
    ) {
        _hue.value = hue
        _saturation.value = saturation
        _value.value = value
        _color.value = Color.hsv(hue, saturation, value)
        // Any color change implies the strip should be on.
        _isOn.value = true
        enqueueColor(throttled = true)
    }

    private fun sendColorNow() = enqueueColor(throttled = false)

    private fun enqueueColor(throttled: Boolean) {
        val c = _color.value
        val frame = ElkProtocol.color(
            (c.red * 255f).roundToInt(),
            (c.green * 255f).roundToInt(),
            (c.blue * 255f).roundToInt()
        )
        if (throttled) colorSendQueue.trySend(frame) else ble.send(frame)
    }

    private fun applySavedStateToDevice() {
        if (_isOn.value) {
            ble.send(ElkProtocol.POWER_ON)
            sendColorNow()
        } else {
            ble.send(ElkProtocol.POWER_OFF)
        }
    }

    // ---- Persistence ------------------------------------------------------

    private fun persistLight() {
        prefs.edit()
            .putBoolean(KEY_POWER, _isOn.value)
            .putFloat(KEY_HUE, _hue.value)
            .putFloat(KEY_SAT, _saturation.value)
            .putFloat(KEY_VAL, _value.value)
            .apply()
    }

    private fun readBoundDevice(): BoundDevice? {
        val address = prefs.getString(KEY_BOUND_ADDR, null) ?: return null
        return BoundDevice(address, prefs.getString(KEY_BOUND_NAME, null))
    }

    override fun onCleared() {
        super.onCleared()
        persistLight()
        ble.stopScan()
        ble.disconnect()
    }

    companion object {
        private const val COLOR_SEND_INTERVAL_MS = 30L
        private const val PING_INTERVAL_MS = 10_000L
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SHOW_ALL = "show_all_devices"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_POWER = "power"
        private const val KEY_HUE = "hue"
        private const val KEY_SAT = "saturation"
        private const val KEY_VAL = "value"
        private const val KEY_BOUND_ADDR = "bound_address"
        private const val KEY_BOUND_NAME = "bound_name"
    }
}
