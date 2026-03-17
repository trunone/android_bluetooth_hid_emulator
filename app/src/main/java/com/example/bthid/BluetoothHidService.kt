package com.example.bthid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.util.concurrent.Executor

class BluetoothHidService : Service() {

    private val binder = LocalBinder()
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var isConnected = false
    private var isRegistered = false
    private var isServiceDestroyed = false

    // Mouse Report Descriptor
    // Usage Page (Generic Desktop), Usage (Mouse), Collection (Application), Usage (Pointer), Collection (Physical),
    // Usage Page (Buttons), Usage Min (1), Usage Max (3), Logical Min (0), Logical Max (1), Report Count (3), Report Size (1), Input (Data, Var, Abs),
    // Report Count (1), Report Size (5), Input (Const),
    // Usage Page (Generic Desktop), Usage (X), Usage (Y), Logical Min (-127), Logical Max (127), Report Size (8), Report Count (2), Input (Data, Var, Rel),
    // End Collection, End Collection
    private val MOUSE_REPORT_DESC = byteArrayOf(
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x02, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte()
    )

    // Keyboard Report Descriptor
    private val KEYBOARD_REPORT_DESC = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01, 0x95.toByte(), 0x05,
        0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x03, 0x91.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte()
    )

    // Composite Descriptor
    // To simplify, I will just use the Mouse Descriptor for now, or try to register both if possible.
    // However, BluetoothHidDevice.registerApp takes only one SDP settings.
    // So usually we concatenate them or use report IDs.
    // For simplicity, let's start with Mouse only to prove the concept.

    // Actually, let's try a composite one with Report IDs.
    // ID 1: Mouse
    // ID 2: Keyboard

    private val COMPOSITE_REPORT_DESC = byteArrayOf(
        // Mouse (Report ID 1)
        // Mouse (Report ID 1)
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01, 0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x02, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte(),

        // Keyboard (Report ID 2)
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02, 0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x03,
        0x95.toByte(), 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x03,
        0x91.toByte(), 0x03, 0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
        0xC0.toByte()
    )


    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=$pluggedDevice")
            isRegistered = registered
            notifyRegistrationStatus(registered)
            if (registered) {
                 // Registered successfully
                 if (pluggedDevice != null) {
                     hostDevice = pluggedDevice
                     isConnected = true
                     notifyConnectionState(true, pluggedDevice)
                 }
            } else {
                 // Registration failed or disconnected
                 hostDevice = null
                 isConnected = false
                 notifyConnectionState(false, null)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "onConnectionStateChanged: device=$device, state=$state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                isConnected = true
                notifyConnectionState(true, device)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                hostDevice = null
                isConnected = false
                notifyConnectionState(false, device)
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID Device Profile Proxy connected")
                bluetoothHidDevice = proxy as BluetoothHidDevice
                // Adding a small delay as some devices fail if registered immediately
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isServiceDestroyed) {
                        registerApp()
                    }
                }, 500)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID Device Profile Proxy disconnected")
                bluetoothHidDevice = null
                isRegistered = false
                notifyRegistrationStatus(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        adapter.getProfileProxy(this, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        super.onDestroy()
        if (bluetoothHidDevice != null) {
            try {
                bluetoothHidDevice?.unregisterApp()
                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidDevice)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDestroy", e)
            }
            bluetoothHidDevice = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        Log.d(TAG, "Attempting to register HID App...")
        
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth HID",
            "Android HID Emulator",
            "Android",
            0x00.toByte(), // Subclass 0 can sometimes be more compatible
            COMPOSITE_REPORT_DESC
        )

        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        val executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }

        try {
            // Setting inQos to null is often more compatible for HID devices
            val result = bluetoothHidDevice?.registerApp(sdpSettings, null, qosSettings, executor, callback)
            Log.d(TAG, "registerApp called, result=$result")
            if (result == false) {
                Log.e(TAG, "registerApp returned false immediately")
                notifyRegistrationStatus(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during registerApp", e)
            notifyRegistrationStatus(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
         try {
            bluetoothHidDevice?.connect(device)
         } catch (e: Exception) {
             Log.e(TAG, "Failed to connect", e)
         }
    }

    @SuppressLint("MissingPermission")
    fun sendMouseReport(dx: Int, dy: Int, leftButton: Boolean, rightButton: Boolean) {
        if (bluetoothHidDevice == null || hostDevice == null) return

        val report = HidUtils.createMouseReport(dx, dy, leftButton, rightButton)

        try {
            bluetoothHidDevice?.sendReport(hostDevice, 1, report) // Report ID 1
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse report", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(modifier: Int, key: Int) {
         if (bluetoothHidDevice == null || hostDevice == null) return

         val report = HidUtils.createKeyboardReport(modifier, key)

         try {
             bluetoothHidDevice?.sendReport(hostDevice, 2, report) // Report ID 2
         } catch (e: Exception) {
             Log.e(TAG, "Error sending keyboard report", e)
         }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    private var connectionListener: ((Boolean, BluetoothDevice?) -> Unit)? = null

    fun setConnectionListener(listener: (Boolean, BluetoothDevice?) -> Unit) {
        this.connectionListener = listener
        // Notify current state
        listener(isConnected, hostDevice)
    }

    private fun notifyConnectionState(connected: Boolean, device: BluetoothDevice?) {
        Handler(Looper.getMainLooper()).post {
            connectionListener?.invoke(connected, device)
        }
    }
    
    private var registrationListener: ((Boolean) -> Unit)? = null

    fun setRegistrationListener(listener: (Boolean) -> Unit) {
        this.registrationListener = listener
        listener(isRegistered)
    }

    private fun notifyRegistrationStatus(registered: Boolean) {
        Handler(Looper.getMainLooper()).post {
            registrationListener?.invoke(registered)
        }
    }

    companion object {
        const val TAG = "BluetoothHidService"
    }
}
