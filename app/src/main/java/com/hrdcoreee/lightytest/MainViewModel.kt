package com.hrdcoreee.lightytest

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrdcoreee.lightytest.ble.BleController
import com.hrdcoreee.lightytest.ble.ConnectionState
import com.hrdcoreee.lightytest.ble.ElkProtocol
import com.hrdcoreee.lightytest.ble.ScannedDevice
import com.hrdcoreee.lightytest.i18n.Language
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * UI-facing state holder. Wraps [BleController] and turns raw color/power
 * intents into throttled ELK frames.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleController(app)

    private val prefs = app.getSharedPreferences("lighty_settings", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(
        Language.fromCode(prefs.getString(KEY_LANGUAGE, null))
    )
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _showAllDevices = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALL, false))
    val showAllDevices: StateFlow<Boolean> = _showAllDevices.asStateFlow()

    val scanning: StateFlow<Boolean> = ble.scanning
    val devices: StateFlow<List<ScannedDevice>> = ble.devices
    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val connectedDevice: StateFlow<ScannedDevice?> = ble.connectedDevice

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    // Color is held as HSV so the picker sliders map cleanly onto the UI.
    private val _hue = MutableStateFlow(0f)          // 0..360
    val hue: StateFlow<Float> = _hue.asStateFlow()

    private val _saturation = MutableStateFlow(1f)    // 0..1
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _value = MutableStateFlow(1f)         // 0..1
    val value: StateFlow<Float> = _value.asStateFlow()

    private val _color = MutableStateFlow(Color.hsv(0f, 1f, 1f))
    val color: StateFlow<Color> = _color.asStateFlow()

    // Conflated: rapid slider updates collapse to the latest color, keeping BLE calm.
    private val colorSendQueue = Channel<ByteArray>(Channel.CONFLATED)

    init {
        ble.setShowAllDevices(_showAllDevices.value)
        viewModelScope.launch {
            for (frame in colorSendQueue) {
                ble.send(frame)
                delay(COLOR_SEND_INTERVAL_MS)
            }
        }
    }

    fun setLanguage(language: Language) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun setShowAllDevices(show: Boolean) {
        _showAllDevices.value = show
        prefs.edit().putBoolean(KEY_SHOW_ALL, show).apply()
        ble.setShowAllDevices(show)
    }

    fun isBluetoothEnabled(): Boolean = ble.isBluetoothEnabled()

    // ---- Scanning ---------------------------------------------------------

    fun toggleScan() = if (scanning.value) ble.stopScan() else ble.startScan()

    fun startScan() = ble.startScan()

    fun stopScan() = ble.stopScan()

    // ---- Connection -------------------------------------------------------

    fun connect(device: ScannedDevice) {
        resetLightState()
        ble.connect(device)
    }

    fun disconnect() = ble.disconnect()

    private fun resetLightState() {
        _isOn.value = false
        _hue.value = 0f
        _saturation.value = 1f
        _value.value = 1f
        _color.value = Color.hsv(0f, 1f, 1f)
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
    }

    fun onHueChange(hue: Float) = updateHsv(hue = hue.coerceIn(0f, 360f))

    fun onSaturationValueChange(saturation: Float, value: Float) =
        updateHsv(saturation = saturation.coerceIn(0f, 1f), value = value.coerceIn(0f, 1f))

    /** Brightness maps directly onto the HSV value channel. */
    fun onBrightnessChange(brightness: Float) =
        updateHsv(value = brightness.coerceIn(0f, 1f))

    fun onHsvChange(hue: Float, saturation: Float, value: Float) =
        updateHsv(
            hue = hue.coerceIn(0f, 360f),
            saturation = saturation.coerceIn(0f, 1f),
            value = value.coerceIn(0f, 1f)
        )

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

    override fun onCleared() {
        super.onCleared()
        ble.stopScan()
        ble.disconnect()
    }

    companion object {
        private const val COLOR_SEND_INTERVAL_MS = 30L
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SHOW_ALL = "show_all_devices"
    }
}
