package com.hrdcoreee.lightytest.ble

/** A device discovered during a BLE scan. */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** True when the advertisement looks like an ELK-BLEDOM controller. */
    val isElk: Boolean
) {
    /** Signal quality bucket, 0 (weak) .. 4 (excellent), from RSSI in dBm. */
    val signalLevel: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -80 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
}

/** Lifecycle of the GATT connection to a single device. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}
