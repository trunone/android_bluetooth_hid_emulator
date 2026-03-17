package com.example.bthid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bthid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvPairedDevices.layoutManager = LinearLayoutManager(this)

        checkPermissions()

        binding.btnMakeDiscoverable.setOnClickListener {
            makeDiscoverable()
        }
    }

    private fun startHidService() {
        if (isBound) return
        val intent = Intent(this, BluetoothHidService::class.java)
        startService(intent) // Start service so it keeps running
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                loadPairedDevices()
                startHidService()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    @android.annotation.SuppressLint("MissingPermission")
    private fun makeDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
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
            loadPairedDevices()
            startHidService()
        }
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadPairedDevices()
            } else {
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show()
            }
        }

    @android.annotation.SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val pairedDevices = adapter.bondedDevices.toList()

        if (pairedDevices.isEmpty()) {
            binding.tvEmptyView.visibility = View.VISIBLE
            binding.rvPairedDevices.visibility = View.GONE
        } else {
            binding.tvEmptyView.visibility = View.GONE
            binding.rvPairedDevices.visibility = View.VISIBLE
            binding.rvPairedDevices.adapter = DeviceAdapter(pairedDevices) { device ->
                connectToDevice(device)
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (isBound && bluetoothService != null) {
            val deviceName = try { device.name ?: "Unknown Device" } catch (e: SecurityException) { "Unknown Device" }
            Toast.makeText(this, "Connecting to $deviceName...", Toast.LENGTH_SHORT).show()

            bluetoothService?.connect(device)
            
            val intent = Intent(this, MouseKeyboardActivity::class.java).apply {
                putExtra("device_address", device.address)
                putExtra("device_name", deviceName)
            }
            startActivity(intent)
        } else {
             Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupServiceListeners() {
        bluetoothService?.setRegistrationListener { registered ->
            runOnUiThread {
                if (registered) {
                    Log.d("MainActivity", "HID Service Registered Successfully")
                    isInitialRegistrationStatus = false
                } else {
                    Log.e("MainActivity", "HID Service Registration Failed or Pending")
                    // Only toast if it's a real failure after we've tried, not just the initial state
                    if (!isInitialRegistrationStatus) {
                        Toast.makeText(this, "HID Registration Failed. Check if your phone supports HID.", Toast.LENGTH_LONG).show()
                    }
                    isInitialRegistrationStatus = false
                }
            }
        }
    }
}
