package app.trustipay.offline.transport.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import app.trustipay.offline.transport.ble.BleGattServer.Companion.CLIENT_CHARACTERISTIC_CONFIG_UUID
import app.trustipay.offline.transport.ble.BleGattServer.Companion.NOTIFY_CHARACTERISTIC_UUID
import app.trustipay.offline.transport.ble.BleGattServer.Companion.PAYMENT_SERVICE_UUID
import app.trustipay.offline.transport.ble.BleGattServer.Companion.WRITE_CHARACTERISTIC_UUID

class BleGattClient(
    private val context: Context,
    private val onReceive: (ByteArray) -> Unit,
) {
    private var gatt: BluetoothGatt? = null
    private var connected = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(PAYMENT_SERVICE_UUID) ?: return
            val notifyChar = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) ?: return
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            descriptor?.let { gatt.writeDescriptor(it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                onReceive(characteristic.value)
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun write(data: ByteArray) {
        val service = gatt?.getService(PAYMENT_SERVICE_UUID) ?: return
        val writeChar = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID) ?: return
        writeChar.value = data
        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt?.writeCharacteristic(writeChar)
    }

    fun isConnected(): Boolean = connected

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connected = false
    }
}
