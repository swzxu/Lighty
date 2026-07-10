package com.hrdcoreee.lightytest.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns everything Bluetooth: scanning, the single GATT connection, and a paced
 * write queue that streams 9-byte ELK-BLEDOM frames to the strip.
 *
 * Permission checks are the caller's responsibility (done in the UI layer before
 * scan/connect), hence the blanket [SuppressLint].
 */
@SuppressLint("MissingPermission")
class BleController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter

    // ---- Observable state -------------------------------------------------

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ScannedDevice?>(null)
    val connectedDevice: StateFlow<ScannedDevice?> = _connectedDevice.asStateFlow()

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    // ---- Scanning ---------------------------------------------------------

    private val found = LinkedHashMap<String, ScannedDevice>()

    @Volatile
    private var showAllDevices = false

    /** When false (default) only ELK-BLEDOM-like controllers are surfaced. */
    fun setShowAllDevices(show: Boolean) {
        showAllDevices = show
        synchronized(found) { emitDevices() }
    }

    // Names used by ELK-BLEDOM and the many clones built on the same chipset.
    private val elkNamePattern = Regex(
        "ELK|BLEDOM|LEDBLE|LED.?BLE|LEDNET|MELK|TRIONES|SP[0-9]{2,3}|ISP|BLE.?LED|KS[0-9]",
        RegexOption.IGNORE_CASE
    )

    private fun isElkLike(name: String?, advertisesElkService: Boolean): Boolean =
        advertisesElkService || (name != null && elkNamePattern.containsMatchIn(name))

    private fun emitDevices() {
        val all = found.values
        val visible = if (showAllDevices) all else all.filter { it.isElk }
        _devices.value = visible.sortedWith(
            compareByDescending<ScannedDevice> { it.isElk }.thenByDescending { it.rssi }
        )
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
            val serviceUuids = record?.serviceUuids
            val advertisesElkService =
                serviceUuids?.any { it.uuid == ElkProtocol.SERVICE_UUID } == true

            val device = ScannedDevice(
                address = result.device.address,
                name = name,
                rssi = result.rssi,
                isElk = isElkLike(name, advertisesElkService)
            )
            synchronized(found) {
                found[device.address] = device
                emitDevices()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
            _scanning.value = false
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (_scanning.value) return
        synchronized(found) {
            found.clear()
            _devices.value = emptyList()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        _scanning.value = true
    }

    fun stopScan() {
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    // ---- Connection -------------------------------------------------------

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeWorker: Job? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTING
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    cleanupConnection()
                    _connectionState.value =
                        if (status == BluetoothGatt.GATT_SUCCESS) ConnectionState.DISCONNECTED
                        else ConnectionState.FAILED
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g
                .getService(ElkProtocol.SERVICE_UUID)
                ?.getCharacteristic(ElkProtocol.WRITE_CHARACTERISTIC_UUID)

            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                writeCharacteristic = characteristic
                runCatching {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                }
                startWriteWorker()
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                Log.w(TAG, "ELK service/characteristic not found (status=$status)")
                _connectionState.value = ConnectionState.FAILED
                g.disconnect()
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    fun connect(device: ScannedDevice) {
        val remote = adapter?.getRemoteDevice(device.address) ?: return
        stopScan()
        disconnect()
        _connectedDevice.value = device
        _connectionState.value = ConnectionState.CONNECTING
        gatt = remote.connectGatt(
            appContext, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE
        )
    }

    fun disconnect() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        cleanupConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanupConnection() {
        writeWorker?.cancel()
        writeWorker = null
        pendingWrite?.complete(false)
        pendingWrite = null
        writeCharacteristic = null
        gatt = null
        _connectedDevice.value = null
    }

    // ---- Write pipeline ---------------------------------------------------

    /** Enqueue a 9-byte ELK frame. Safe to spam; writes are paced by the stack. */
    fun send(command: ByteArray) {
        writeQueue.trySend(command)
    }

    private fun startWriteWorker() {
        writeWorker?.cancel()
        writeWorker = scope.launch {
            for (command in writeQueue) {
                performWrite(command)
            }
        }
    }

    private suspend fun performWrite(value: ByteArray) {
        val g = gatt ?: return
        val characteristic = writeCharacteristic ?: return

        val writeType =
            if (characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            ) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val ack = CompletableDeferred<Boolean>()
        pendingWrite = ack

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, value, writeType) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = writeType
                characteristic.value = value
                g.writeCharacteristic(characteristic)
            }
        }

        if (!started) {
            pendingWrite = null
            return
        }
        // Wait for the stack to acknowledge before issuing the next frame.
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { ack.await() }
        pendingWrite = null
    }

    companion object {
        private const val TAG = "BleController"
        private const val WRITE_TIMEOUT_MS = 600L
    }
}
