package io.github.trunone.bluetooth_hid_emulator

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HidReportTest {

    @Test
    fun testMouseReport() {
        val dx = 10
        val dy = -5
        val leftButton = true
        val rightButton = false

        val expected = byteArrayOf(1, 10, -5, 0, 0)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun testMouseReportBothButtons() {
        val dx = 0
        val dy = 0
        val leftButton = true
        val rightButton = true

        val expected = byteArrayOf(3, 0, 0, 0, 0)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun testMouseReportWithScroll() {
        val dx = 0
        val dy = 0
        val leftButton = false
        val rightButton = false
        val vScroll = 15
        val hScroll = -8

        val expected = byteArrayOf(0, 0, 0, 15, -8)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton, vScroll, hScroll)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun testMouseReportScrollClamping() {
        val dx = 200
        val dy = -200
        val leftButton = false
        val rightButton = false
        val vScroll = 150
        val hScroll = -150

        val expected = byteArrayOf(0, 127, -127, 127, -127)
        val actual = HidUtils.createMouseReport(dx, dy, leftButton, rightButton, vScroll, hScroll)

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

    @Test
    fun testAndroidKeyCodeToHidMapping() {
        assertEquals(HidUtils.KEY_UP, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(HidUtils.KEY_DOWN, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(HidUtils.KEY_LEFT, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(HidUtils.KEY_RIGHT, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))

        assertEquals(HidUtils.KEY_F1, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_F1))
        assertEquals(HidUtils.KEY_F5, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_F5))
        assertEquals(HidUtils.KEY_F12, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_F12))

        assertEquals(HidUtils.KEY_ESC, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(HidUtils.KEY_TAB, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_TAB))
        assertEquals(HidUtils.KEY_BACKSPACE, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_DEL))
        assertEquals(HidUtils.KEY_DELETE, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(HidUtils.KEY_HOME, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(HidUtils.KEY_END, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_MOVE_END))
        assertEquals(HidUtils.KEY_PAGE_UP, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(HidUtils.KEY_PAGE_DOWN, HidUtils.getHidCodeFromAndroidKeyCode(KeyEvent.KEYCODE_PAGE_DOWN))
    }

    @Test
    fun testCharToHidMapping() {
        val aResult = HidUtils.getHidKeyForChar('a')
        assertNotNull(aResult)
        assertEquals(0, aResult!!.first)
        assertEquals(0x04, aResult.second)

        val capAResult = HidUtils.getHidKeyForChar('A')
        assertNotNull(capAResult)
        assertEquals(HidUtils.MOD_LEFT_SHIFT, capAResult!!.first)
        assertEquals(0x04, capAResult.second)

        val exclaimResult = HidUtils.getHidKeyForChar('!')
        assertNotNull(exclaimResult)
        assertEquals(HidUtils.MOD_LEFT_SHIFT, exclaimResult!!.first)
        assertEquals(0x1E, exclaimResult.second)
    }
}
