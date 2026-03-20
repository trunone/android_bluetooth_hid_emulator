package io.github.trunone.bluetooth_hid_emulator

object HidUtils {
    fun createMouseReport(dx: Int, dy: Int, leftButton: Boolean, rightButton: Boolean): ByteArray {
        var buttons = 0
        if (leftButton) buttons = buttons or 1
        if (rightButton) buttons = buttons or 2

        val report = ByteArray(3)
        report[0] = buttons.toByte()
        // Clamp to -127 to 127 to avoid byte overflow for relative movement
        report[1] = dx.coerceIn(-127, 127).toByte()
        report[2] = dy.coerceIn(-127, 127).toByte()
        return report
    }

    fun createKeyboardReport(modifier: Int, key: Int): ByteArray {
        val report = ByteArray(8)
        report[0] = modifier.toByte()
        report[1] = 0 // Reserved
        report[2] = key.toByte()
        // 3-7 are 0
        return report
    }
}
