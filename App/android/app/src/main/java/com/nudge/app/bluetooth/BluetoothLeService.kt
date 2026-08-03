package com.nudge.app.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothLeService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val deserializer = PacketDeserializer()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _receivedPackets = MutableSharedFlow<Packet>(extraBufferCapacity = 64)
    val receivedPackets = _receivedPackets.asSharedFlow()

    private var lastCallbackTime = 0L
    private var lastCallbackValueHash = 0

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "nudge_channel",
            "Nudge Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "nudge_channel")
            .setContentTitle("Nudge Tracking")
            .setContentText("Tracking data from ESP32...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }

    fun startScanning() {
        _discoveredDevices.value = emptyList()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    fun connectToDevice(address: String) {
        // Disconnect and close existing GATT to prevent double-callbacks
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    fun sendMessage(data: ByteArray) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.e("BLE", "Cannot send message: GATT is null")
            return
        }
        
        val service = gatt.getService(SERVICE_RX_UUID)
        if (service == null) {
            Log.e("BLE", "RX Service not found: $SERVICE_RX_UUID")
            return
        }
        
        val characteristic = service.getCharacteristic(CHAR_RX_UUID)
        if (characteristic != null) {
            Log.d("BLE", "Sending ${data.size} bytes to characteristic $CHAR_RX_UUID using WRITE_TYPE_DEFAULT")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val success = @Suppress("DEPRECATION") gatt.writeCharacteristic(characteristic)
                Log.d("BLE", "writeCharacteristic success: $success")
            }
        } else {
            Log.e("BLE", "RX Characteristic not found: $CHAR_RX_UUID")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null && !_discoveredDevices.value.any { it.address == device.address }) {
                _discoveredDevices.value = _discoveredDevices.value + device
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            _connectionState.value = newState
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected to GATT server. Requesting MTU...")
                gatt.requestMtu(517)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Disconnected from GATT server.")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "MTU changed to: $mtu. Discovering services...")
                gatt.discoverServices()
            } else {
                Log.e("BLE", "MTU request failed with status: $status. Discovering services anyway...")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Services discovered successfully.")
                gatt.services.forEach { service ->
                    Log.d("BLE", "Discovered Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d("BLE", "  - Characteristic: ${char.uuid}")
                    }
                }

                val service = gatt.getService(SERVICE_TX_UUID)
                if (service == null) {
                    Log.e("BLE", "TX Service not found! Looking for: $SERVICE_TX_UUID")
                    gatt.services.forEach { Log.d("BLE", "Available Service: ${it.uuid}") }
                    return
                }

                val characteristic = service.getCharacteristic(CHAR_TX_UUID)
                if (characteristic != null) {
                    Log.i("BLE", "TX Characteristic found. Enabling notifications...")
                    val success = gatt.setCharacteristicNotification(characteristic, true)
                    Log.d("BLE", "setCharacteristicNotification success: $success")

                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                        Log.i("BLE", "Notification descriptor write initiated.")
                    } else {
                        Log.e("BLE", "CCCD Descriptor not found on characteristic!")
                    }
                } else {
                    Log.e("BLE", "TX Characteristic not found! Looking for: $CHAR_TX_UUID")
                    service.characteristics.forEach { Log.d("BLE", "Available Char: ${it.uuid}") }
                }
            } else {
                Log.e("BLE", "Service discovery failed with status: $status")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Notification subscription CONFIRMED by hardware for ${descriptor.characteristic.uuid}")
            } else {
                Log.e("BLE", "Notification subscription FAILED with status: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Write SUCCESS to ${characteristic.uuid}")
            } else {
                Log.e("BLE", "Write FAILED to ${characteristic.uuid} with status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleDataChange(characteristic.value)
        }
        
        // Android 13+ support
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleDataChange(value)
        }

        private fun handleDataChange(value: ByteArray) {
            val currentTime = System.currentTimeMillis()
            val valueHash = value.contentHashCode()

            // Deduplicate: Ignore if it's the same data arriving within 10ms (common double-callback bug)
            if (currentTime - lastCallbackTime < 10 && valueHash == lastCallbackValueHash) {
                return
            }
            lastCallbackTime = currentTime
            lastCallbackValueHash = valueHash

            val hexString = value.joinToString("-") { "%02X".format(it) }
            Log.d("BLE", "Raw Data (${value.size} bytes): $hexString")

            val packet = deserializer.processSegment(value)
            if (packet != null) {
                _receivedPackets.tryEmit(packet)
            }
        }
    }

    companion object {
        val SERVICE_TX_UUID: UUID = UUID.fromString("000B1E53-D47A-CEDE-DE57-000000008488")
        val SERVICE_RX_UUID: UUID = UUID.fromString("000B1E53-D47A-CEDE-DE57-000000008288")
        val CHAR_TX_UUID: UUID = UUID.fromString("00008488-D47A-CEDE-0000-466178454d47")
        val CHAR_RX_UUID: UUID = UUID.fromString("00008288-D47A-CEDE-0000-526563436d64")
    }
}
