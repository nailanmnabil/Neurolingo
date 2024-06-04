package top.nabil.neurolingo.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

data class BluetoothDeviceDomain(
    val name: String = "unknown bluetooth",
    val address: String
)

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(
        name = this?.name ?: "Tidak diketahui",
        address = this.address ?: "Tidak diketahui"
    )
}