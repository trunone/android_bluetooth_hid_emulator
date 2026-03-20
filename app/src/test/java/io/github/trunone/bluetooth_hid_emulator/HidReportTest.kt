package io.github.trunone.bluetooth_hid_emulator

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HidReportTest {

    @Test
    fun testMouseReport() {
        val dx = 10
        val dy = -5
        val leftButton = true
        val rightButton = false

        val expected = byteArrayOf(1, 10, -5)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun testMouseReportBothButtons() {
        val dx = 0
        val dy = 0
        val leftButton = true
        val rightButton = true

        val expected = byteArrayOf(3, 0, 0)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun testKeyboardReport() {
        val modifier = 0x02 // Shift
        val key = 0x04 // 'a' or 'A'

        val expected = byteArrayOf(0x02, 0, 0x04, 0, 0, 0, 0, 0)
        val actual = HidUtils.createKeyboardReport(modifier, key)

        assertArrayEquals(expected, actual)
    }
}
