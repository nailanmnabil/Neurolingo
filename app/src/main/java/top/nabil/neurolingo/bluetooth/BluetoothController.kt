package top.nabil.neurolingo.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BluetoothController(
    private val context: Context
) {
    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var isReceiverRegistered = false
    private val scannerReceiver: ScannerReceiver = ScannerReceiver { device ->
        val newDevice = device.toBluetoothDeviceDomain()
        _scannedDevice.update { devices ->
            if (newDevice in devices) devices else devices + newDevice
        }
    }

    private val _pairedDevice = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val pairedDevice = _pairedDevice.asStateFlow()

    private val _scannedDevice = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevice = _scannedDevice.asStateFlow()

    private var scanJob: Job? = null
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    init {
        updatePairedDevices()
    }

    fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        _isScanning.update { true }

        context.registerReceiver(
            scannerReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()

        scanJob = CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(scannerReceiver)
            isReceiverRegistered = false
        }

        scanJob?.cancel()

        _isScanning.update { false }
    }

    private fun updatePairedDevices() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }

        bluetoothAdapter
            ?.bondedDevices
            ?.map {
                it.toBluetoothDeviceDomain()
            }
            .also { newVal ->
                if (newVal != null) {
                    _pairedDevice.update {
                        newVal
                    }
                }
            }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
