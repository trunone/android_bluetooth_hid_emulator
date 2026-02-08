package com.example.bthid

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
import com.example.bthid.databinding.ActivityMouseKeyboardBinding

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

                    // Send relative movement
                    if (dx != 0 || dy != 0) {
                        bluetoothService?.sendMouseReport(dx, dy, leftButtonDown, rightButtonDown)
                        lastX = event.x
                        lastY = event.y
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
                if (count > 0 && s != null) {
                    val char = s[start + count - 1]
                    sendKey(char)
                } else if (count == 0 && before > 0) {
                    sendKey('\u0008')
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Clear the text so we can type the same character again comfortably?
                // Or just keep appending. If we clear, we might lose context or cursor position.
                // Let's clear for now to act as a pure input pipe.
                if (s != null && s.isNotEmpty()) {
                     s.clear()
                }
            }
        })
    }

    private fun sendKey(char: Char) {
        // Map char to HID keycode. This is complex.
        // For simplicity, handle lowercase a-z and 0-9.

        var keycode = 0
        var modifier = 0

        if (char in 'a'..'z') {
            keycode = 0x04 + (char - 'a')
        } else if (char in 'A'..'Z') {
            keycode = 0x04 + (char - 'A')
            modifier = 0x02 // Shift
        } else if (char in '1'..'9') {
            keycode = 0x1E + (char - '1')
        } else if (char == '0') {
            keycode = 0x27
        } else if (char == ' ') {
            keycode = 0x2C
        } else if (char == '\n') {
            keycode = 0x28
        } else if (char == '\u0008') {
            keycode = 0x2A
        }

        if (keycode != 0) {
            // Key Down
            bluetoothService?.sendKeyboardReport(modifier, keycode)
            // Key Up (immediately)
            bluetoothService?.sendKeyboardReport(0, 0)
        }
    }
}
