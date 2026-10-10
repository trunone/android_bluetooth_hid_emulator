package io.github.trunone.bluetooth_hid_emulator

import android.view.KeyEvent

object HidUtils {
    // Modifier flags
    const val MOD_NONE = 0x00
    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    // Key Codes
    const val KEY_NONE = 0x00
    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    const val KEY_F1 = 0x3A
    const val KEY_F2 = 0x3B
    const val KEY_F3 = 0x3C
    const val KEY_F4 = 0x3D
    const val KEY_F5 = 0x3E
    const val KEY_F6 = 0x3F
    const val KEY_F7 = 0x40
    const val KEY_F8 = 0x41
    const val KEY_F9 = 0x42
    const val KEY_F10 = 0x43
    const val KEY_F11 = 0x44
    const val KEY_F12 = 0x45

    fun createMouseReport(
        dx: Int,
        dy: Int,
        leftButton: Boolean,
        rightButton: Boolean,
        vScroll: Int = 0,
        hScroll: Int = 0
    ): ByteArray {
        var buttons = 0
        if (leftButton) buttons = buttons or 1
        if (rightButton) buttons = buttons or 2

        val report = ByteArray(5)
        report[0] = buttons.toByte()
        // Clamp to -127 to 127 to avoid byte overflow for relative movement
        report[1] = dx.coerceIn(-127, 127).toByte()
        report[2] = dy.coerceIn(-127, 127).toByte()
        report[3] = vScroll.coerceIn(-127, 127).toByte()
        report[4] = hScroll.coerceIn(-127, 127).toByte()
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

    fun getHidCodeFromAndroidKeyCode(androidKeyCode: Int): Int {
        return when (androidKeyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_RIGHT
            KeyEvent.KEYCODE_F1 -> KEY_F1
            KeyEvent.KEYCODE_F2 -> KEY_F2
            KeyEvent.KEYCODE_F3 -> KEY_F3
            KeyEvent.KEYCODE_F4 -> KEY_F4
            KeyEvent.KEYCODE_F5 -> KEY_F5
            KeyEvent.KEYCODE_F6 -> KEY_F6
            KeyEvent.KEYCODE_F7 -> KEY_F7
            KeyEvent.KEYCODE_F8 -> KEY_F8
            KeyEvent.KEYCODE_F9 -> KEY_F9
            KeyEvent.KEYCODE_F10 -> KEY_F10
            KeyEvent.KEYCODE_F11 -> KEY_F11
            KeyEvent.KEYCODE_F12 -> KEY_F12
            KeyEvent.KEYCODE_ESCAPE -> KEY_ESC
            KeyEvent.KEYCODE_TAB -> KEY_TAB
            KeyEvent.KEYCODE_DEL -> KEY_BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> KEY_DELETE
            KeyEvent.KEYCODE_INSERT -> KEY_INSERT
            KeyEvent.KEYCODE_MOVE_HOME -> KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> KEY_PAGE_DOWN
            KeyEvent.KEYCODE_ENTER -> KEY_ENTER
            KeyEvent.KEYCODE_SPACE -> KEY_SPACE
            else -> KEY_NONE
        }
    }

    fun getHidKeyForChar(char: Char): Pair<Int, Int>? {
        val keycode: Int
        var modifier = 0

        when (char) {
            in 'a'..'z' -> keycode = 0x04 + (char - 'a')
            in 'A'..'Z' -> {
                keycode = 0x04 + (char - 'A')
                modifier = MOD_LEFT_SHIFT
            }
            in '1'..'9' -> keycode = 0x1E + (char - '1')
            '0' -> keycode = 0x27
            ' ' -> keycode = KEY_SPACE
            '\n' -> keycode = KEY_ENTER
            '\u0008' -> keycode = KEY_BACKSPACE
            '\t' -> keycode = KEY_TAB
            '`' -> keycode = 0x35
            '~' -> { keycode = 0x35; modifier = MOD_LEFT_SHIFT }
            '!' -> { keycode = 0x1E; modifier = MOD_LEFT_SHIFT }
            '@' -> { keycode = 0x1F; modifier = MOD_LEFT_SHIFT }
            '#' -> { keycode = 0x20; modifier = MOD_LEFT_SHIFT }
            '$' -> { keycode = 0x21; modifier = MOD_LEFT_SHIFT }
            '%' -> { keycode = 0x22; modifier = MOD_LEFT_SHIFT }
            '^' -> { keycode = 0x23; modifier = MOD_LEFT_SHIFT }
            '&' -> { keycode = 0x24; modifier = MOD_LEFT_SHIFT }
            '*' -> { keycode = 0x25; modifier = MOD_LEFT_SHIFT }
            '(' -> { keycode = 0x26; modifier = MOD_LEFT_SHIFT }
            ')' -> { keycode = 0x27; modifier = MOD_LEFT_SHIFT }
            '-' -> keycode = 0x2D
            '_' -> { keycode = 0x2D; modifier = MOD_LEFT_SHIFT }
            '=' -> keycode = 0x2E
            '+' -> { keycode = 0x2E; modifier = MOD_LEFT_SHIFT }
            '[' -> keycode = 0x2F
            '{' -> { keycode = 0x2F; modifier = MOD_LEFT_SHIFT }
            ']' -> keycode = 0x30
            '}' -> { keycode = 0x30; modifier = MOD_LEFT_SHIFT }
            '\\' -> keycode = 0x31
            '|' -> { keycode = 0x31; modifier = MOD_LEFT_SHIFT }
            ';' -> keycode = 0x33
            ':' -> { keycode = 0x33; modifier = MOD_LEFT_SHIFT }
            '\'' -> keycode = 0x34
            '"' -> { keycode = 0x34; modifier = MOD_LEFT_SHIFT }
            ',' -> keycode = 0x36
            '<' -> { keycode = 0x36; modifier = MOD_LEFT_SHIFT }
            '.' -> keycode = 0x37
            '>' -> { keycode = 0x37; modifier = MOD_LEFT_SHIFT }
            '/' -> keycode = 0x38
            '?' -> { keycode = 0x38; modifier = MOD_LEFT_SHIFT }
            else -> return null
        }
        return Pair(modifier, keycode)
    }
}
