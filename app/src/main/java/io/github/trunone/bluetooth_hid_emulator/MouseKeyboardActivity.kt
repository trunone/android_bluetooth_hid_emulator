package io.github.trunone.bluetooth_hid_emulator

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.trunone.bluetooth_hid_emulator.databinding.ActivityMouseKeyboardBinding

class MouseKeyboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMouseKeyboardBinding
    private var bluetoothService: BluetoothHidService? = null
    private var isBound = false
    private var isInitialRegistrationStatus = true

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

        updateDeviceText(null)

        setupTouchpad()
        setupButtons()
        setupKeyboard()

        checkPermissions()
    }

    private fun startHidService() {
        if (isBound) return
        val intent = Intent(this, BluetoothHidService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startHidService()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startHidService()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bluetooth_devices -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun updateDeviceText(device: android.bluetooth.BluetoothDevice?) {
        if (device != null) {
            val name = try { device.name } catch (e: SecurityException) { null }
            binding.tvDeviceName.text = name ?: device.address
        } else {
            binding.tvDeviceName.text = getString(R.string.status_disconnected)
        }
    }

    private fun setupServiceListeners() {
        bluetoothService?.setConnectionListener { connected, device ->
            runOnUiThread {
                if (connected) {
                    binding.tvStatus.text = getString(R.string.status_connected)
                    updateDeviceText(device)
                } else {
                    binding.tvStatus.text = getString(R.string.status_disconnected)
                    updateDeviceText(null)
                }
            }
        }

        bluetoothService?.setRegistrationListener { registered ->
            runOnUiThread {
                if (registered) {
                    Log.d("MouseKeyboardActivity", "HID Service Registered Successfully")
                    isInitialRegistrationStatus = false
                } else {
                    Log.e("MouseKeyboardActivity", "HID Service Registration Failed or Pending")
                    if (!isInitialRegistrationStatus) {
                        Toast.makeText(this, "HID Registration Failed. Check if your phone supports HID.", Toast.LENGTH_LONG).show()
                    }
                    isInitialRegistrationStatus = false
                }
            }
        }
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var isScrolling = false

    private var isClearingText = false

    private fun setupTouchpad() {
        binding.viewTouchpad.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()
                    lastX = event.x
                    lastY = event.y
                    isScrolling = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        isScrolling = true
                        lastScrollX = (event.getX(0) + event.getX(1)) / 2f
                        lastScrollY = (event.getY(0) + event.getY(1)) / 2f
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val currentScrollX = (event.getX(0) + event.getX(1)) / 2f
                        val currentScrollY = (event.getY(0) + event.getY(1)) / 2f

                        val totalHScroll = (currentScrollX - lastScrollX).toInt()
                        val totalVScroll = (lastScrollY - currentScrollY).toInt() // Swipe down -> scroll down (negative wheel delta)

                        val hScrollSent = totalHScroll.coerceIn(-127, 127)
                        val vScrollSent = totalVScroll.coerceIn(-127, 127)

                        if (hScrollSent != 0 || vScrollSent != 0) {
                            bluetoothService?.sendMouseReport(
                                dx = 0,
                                dy = 0,
                                leftButton = leftButtonDown,
                                rightButton = rightButtonDown,
                                vScroll = vScrollSent,
                                hScroll = hScrollSent
                            )
                            lastScrollX += hScrollSent
                            lastScrollY -= vScrollSent
                        }
                    } else if (!isScrolling && event.pointerCount == 1) {
                        val dx = (event.x - lastX).toInt()
                        val dy = (event.y - lastY).toInt()

                        val dxSent = dx.coerceIn(-127, 127)
                        val dySent = dy.coerceIn(-127, 127)

                        if (dxSent != 0 || dySent != 0) {
                            bluetoothService?.sendMouseReport(
                                dx = dxSent,
                                dy = dySent,
                                leftButton = leftButtonDown,
                                rightButton = rightButtonDown
                            )
                            lastX += dxSent
                            lastY += dySent
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        // Returning to single touch or releasing multi-touch
                        val activePointerIndex = if (event.actionIndex == 0) 1 else 0
                        if (activePointerIndex < event.pointerCount) {
                            lastX = event.getX(activePointerIndex)
                            lastY = event.getY(activePointerIndex)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isScrolling = false
                    true
                }
                else -> false
            }
        }
    }

    private var leftButtonDown = false
    private var rightButtonDown = false

    private fun setupButtons() {
        binding.btnLeftClick.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()
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

        binding.btnRightClick.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.performClick()
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
        binding.etKeyboardInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    sendKey('\u0008')
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    sendKey('\n')
                    return@setOnKeyListener true
                }
            }
            false
        }

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
            '`' -> keycode = 0x35
            '~' -> { keycode = 0x35; modifier = 0x02 }
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
