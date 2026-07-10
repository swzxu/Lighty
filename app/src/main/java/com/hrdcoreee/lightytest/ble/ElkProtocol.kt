package com.hrdcoreee.lightytest.ble

import java.util.UUID

/**
 * ELK-BLEDOM control protocol (verified in practice).
 *
 * Service:        0000fff0-0000-1000-8000-00805f9b34fb
 * Write char:     0000fff3-0000-1000-8000-00805f9b34fb
 *
 * Every command is a fixed 9-byte frame: 0x7E ... 0xEF.
 * Byte 1 is always 0x07, byte 2 selects the command type.
 */
object ElkProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    /** Turn the strip on. Also forces the controller into static-color mode. */
    val POWER_ON: ByteArray = frame(0x7E, 0x07, 0x04, 0xFF, 0x00, 0x01, 0x02, 0x01, 0xEF)

    /** Turn the strip off. */
    val POWER_OFF: ByteArray = frame(0x7E, 0x07, 0x04, 0x00, 0x00, 0x00, 0x02, 0x01, 0xEF)

    /**
     * Static color command: 7E 07 05 03 RR GG BB 00 EF
     * Sending a color also switches the strip into manual mode and lights it up.
     */
    fun color(r: Int, g: Int, b: Int): ByteArray = frame(
        0x7E, 0x07, 0x05, 0x03,
        r and 0xFF, g and 0xFF, b and 0xFF,
        0x00, 0xEF
    )

    private fun frame(vararg bytes: Int): ByteArray =
        ByteArray(bytes.size) { bytes[it].toByte() }
}
