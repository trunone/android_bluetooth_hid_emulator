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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                loadPairedDevices()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvPairedDevices.layoutManager = LinearLayoutManager(this)

        checkPermissions()

        val intent = Intent(this, BluetoothHidService::class.java)
        startService(intent) // Start service so it keeps running
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
             Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
             // Could launch intent to enable bluetooth
             return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            bluetoothService?.connect(device)
            // Navigate to MouseKeyboardActivity immediately, or wait for connection?
            // Usually connection takes time. But the user expects to be in the control UI.
            // Let's pass the device info to the next activity.
            val intent = Intent(this, MouseKeyboardActivity::class.java).apply {
                putExtra("device_address", device.address)
                putExtra("device_name", device.name)
            }
            startActivity(intent)
        }
    }

    private fun setupServiceListeners() {
        // Here we could listen for connection updates if we wanted to show status in the list
    }
}
