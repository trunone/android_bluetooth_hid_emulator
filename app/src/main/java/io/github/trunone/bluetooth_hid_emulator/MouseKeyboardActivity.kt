package io.github.trunone.bluetooth_hid_emulator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.trunone.bluetooth_hid_emulator.databinding.ActivityMouseKeyboardBinding

class MouseKeyboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMouseKeyboardBinding
    private var bluetoothService: BluetoothHidService? = null
    private var isBound = false
    private var deviceAddress: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothHidService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            setupServiceListeners()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            bluetoothService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMouseKeyboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceAddress = intent.getStringExtra("device_address")
        val deviceName = intent.getStringExtra("device_name")
        binding.tvDeviceName.text = deviceName ?: "Unknown Device"

        setupTouchpad()
        setupButtons()
        setupKeyboard()

        val intent = Intent(this, BluetoothHidService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun setupServiceListeners() {
        bluetoothService?.setConnectionListener { connected, _ ->
            runOnUiThread {
                if (connected) {
                    binding.tvStatus.text = getString(R.string.status_connected)
                } else {
                    binding.tvStatus.text = getString(R.string.status_disconnected)
                }
            }
        }
    }

    private var lastX = 0f
    private var lastY = 0f

    private var isClearingText = false

    private fun setupTouchpad() {
        binding.viewTouchpad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX).toInt()
                    val dy = (event.y - lastY).toInt()

                    // Send relative movement, but only as much as HID supports in one report (-127 to 127)
                    val dxSent = dx.coerceIn(-127, 127)
                    val dySent = dy.coerceIn(-127, 127)

                    if (dxSent != 0 || dySent != 0) {
                        bluetoothService?.sendMouseReport(dxSent, dySent, leftButtonDown, rightButtonDown)
                        // Only update lastX/lastY by what was actually sent to keep the remainder for next event
                        lastX += dxSent
                        lastY += dySent
                    }
                    true
                }
                else -> false
            }
        }
    }

    private var leftButtonDown = false
    private var rightButtonDown = false

    private fun setupButtons() {
        binding.btnLeftClick.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    leftButtonDown = true
                    bluetoothService?.sendMouseReport(0, 0, leftButtonDown, rightButtonDown)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    leftButtonDown = false
                    bluetoothService?.sendMouseReport(0, 0, leftButtonDown, rightButtonDown)
                    true
                }
                else -> false
            }
        }

        binding.btnRightClick.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rightButtonDown = true
                    bluetoothService?.sendMouseReport(0, 0, leftButtonDown, rightButtonDown)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    rightButtonDown = false
                    bluetoothService?.sendMouseReport(0, 0, leftButtonDown, rightButtonDown)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupKeyboard() {
        binding.etKeyboardInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isClearingText) return

                if (count > 0 && s != null) {
                    // Send all new characters
                    for (i in 0 until count) {
                        sendKey(s[start + i])
                    }
                } else if (count == 0 && before > 0) {
                    sendKey('\u0008')
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    isClearingText = true
                    s.clear()
                    isClearingText = false
                }
            }
        })
    }

    private fun sendKey(char: Char) {
        // Map char to HID keycode. This is complex.
        // For simplicity, handle lowercase a-z and 0-9.

        var keycode = 0
        var modifier = 0

        when (char) {
            in 'a'..'z' -> keycode = 0x04 + (char - 'a')
            in 'A'..'Z' -> {
                keycode = 0x04 + (char - 'A')
                modifier = 0x02 // Left Shift
            }
            in '1'..'9' -> keycode = 0x1E + (char - '1')
            '0' -> keycode = 0x27
            ' ' -> keycode = 0x2C
            '\n' -> keycode = 0x28
            '\u0008' -> keycode = 0x2A // Backspace
            '\t' -> keycode = 0x2B // Tab
            '!' -> { keycode = 0x1E; modifier = 0x02 }
            '@' -> { keycode = 0x1F; modifier = 0x02 }
            '#' -> { keycode = 0x20; modifier = 0x02 }
            '$' -> { keycode = 0x21; modifier = 0x02 }
            '%' -> { keycode = 0x22; modifier = 0x02 }
            '^' -> { keycode = 0x23; modifier = 0x02 }
            '&' -> { keycode = 0x24; modifier = 0x02 }
            '*' -> { keycode = 0x25; modifier = 0x02 }
            '(' -> { keycode = 0x26; modifier = 0x02 }
            ')' -> { keycode = 0x27; modifier = 0x02 }
            '-' -> keycode = 0x2D
            '_' -> { keycode = 0x2D; modifier = 0x02 }
            '=' -> keycode = 0x2E
            '+' -> { keycode = 0x2E; modifier = 0x02 }
            '[' -> keycode = 0x2F
            '{' -> { keycode = 0x2F; modifier = 0x02 }
            ']' -> keycode = 0x30
            '}' -> { keycode = 0x30; modifier = 0x02 }
            '\\' -> keycode = 0x31
            '|' -> { keycode = 0x31; modifier = 0x02 }
            ';' -> keycode = 0x33
            ':' -> { keycode = 0x33; modifier = 0x02 }
            '\'' -> keycode = 0x34
            '"' -> { keycode = 0x34; modifier = 0x02 }
            ',' -> keycode = 0x36
            '<' -> { keycode = 0x36; modifier = 0x02 }
            '.' -> keycode = 0x37
            '>' -> { keycode = 0x37; modifier = 0x02 }
            '/' -> keycode = 0x38
            '?' -> { keycode = 0x38; modifier = 0x02 }
        }

        if (keycode != 0) {
            // Key Down
            bluetoothService?.sendKeyboardReport(modifier, keycode)
            // Key Up (immediately)
            bluetoothService?.sendKeyboardReport(0, 0)
        }
    }
}
