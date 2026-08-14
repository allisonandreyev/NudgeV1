package com.nudge.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.DataPoint
import com.nudge.app.data.DataPointDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataPointDao: DataPointDao
) : ViewModel() {

    private var bluetoothService: BluetoothLeService? = null
    private var isBound = false
    private var collectionJob: Job? = null

    private val _currentUsername = MutableStateFlow<String?>(null)
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    private val _activeLabel = MutableStateFlow<String?>(null)

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    // Multi-channel data history
    private val _emgDataD0 = MutableStateFlow<List<Float>>(emptyList())
    val emgDataD0 = _emgDataD0.asStateFlow()

    private val _emgDataD1 = MutableStateFlow<List<Float>>(emptyList())
    val emgDataD1 = _emgDataD1.asStateFlow()

    private val _emgDataD2 = MutableStateFlow<List<Float>>(emptyList())
    val emgDataD2 = _emgDataD2.asStateFlow()

    private val _lastGesture = MutableStateFlow("UNKNOWN")
    val lastGesture = _lastGesture.asStateFlow()

    private val _receiveFrequency = MutableStateFlow(0f)
    val receiveFrequency = _receiveFrequency.asStateFlow()

    private val _rawLogs = MutableStateFlow<List<String>>(emptyList())
    val rawLogs = _rawLogs.asStateFlow()

    private var lastMessageId = -1
    private var lastReceiveTime = 0L
    private val receiveDeltas = mutableListOf<Long>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothLeService.LocalBinder
            val s = binder.getService()
            bluetoothService = s
            isBound = true

            // Cancel any existing collections to prevent duplicates
            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                // Launch child coroutines for each flow
                launch {
                    s.discoveredDevices.collect { _discoveredDevices.value = it }
                }
                launch {
                    s.connectionState.collect { _connectionState.value = it }
                }
                launch {
                    s.receivedPackets.collect { packet ->
                        handlePacket(packet)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    init {
        val intent = Intent(context, BluetoothLeService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun setCurrentUser(username: String) {
        _currentUsername.value = username
    }

    fun setActiveSession(sessionId: Long?) {
        _activeSessionId.value = sessionId
    }

    fun setActiveLabel(label: String?) {
        _activeLabel.value = label
    }

    private fun handlePacket(packet: Packet) {
        if (packet.messageId == lastMessageId) {
            Log.d("BluetoothViewModel", "Ignoring duplicate packet: msgId=${packet.messageId}")
            return
        }
        lastMessageId = packet.messageId

        // Log.d("BluetoothViewModel", "Handling packet: msgId=${packet.messageId}, dataSize=${packet.data.size}, values=${packet.data}")
        val currentTime = System.currentTimeMillis()
        if (lastReceiveTime != 0L) {
            val delta = currentTime - lastReceiveTime
            if (delta > 0) {
                receiveDeltas.add(delta)
                if (receiveDeltas.size > 20) receiveDeltas.removeAt(0)
                val avgDelta = receiveDeltas.average()
                _receiveFrequency.value = (1000.0 / avgDelta).toFloat()
            }
        }
        lastReceiveTime = currentTime

        // Update raw logs
        _rawLogs.update { (it + "MsgID: ${packet.messageId}, Data: ${packet.data}").takeLast(50) }

        // The packet.data list contains the decoded TLV values.
        // We expect up to 3 values: D0, D1, D2 in order.
        // Optional 4th value: AI Classification result
        packet.data.forEachIndexed { index, value ->
            if (index == 3) {
                // Potential AI result
                val gesture = when (value) {
                    is String -> value
                    is Number -> {
                        when (value.toInt()) {
                            0 -> "CLOSE"
                            1 -> "OPEN"
                            2 -> "PINCH"
                            3 -> "REST"
                            else -> "UNKNOWN"
                        }
                    }
                    else -> "???"
                }
                _lastGesture.value = gesture
                return@forEachIndexed
            }

            val floatValue = when (value) {
                is Number -> value.toFloat()
                is UByte -> value.toFloat()
                is UShort -> value.toFloat()
                is UInt -> value.toFloat()
                is ULong -> value.toFloat()
                else -> null
            }

            if (floatValue != null && index < 3) {
                // Update the correct flow based on index
                when (index) {
                    0 -> _emgDataD0.update { (it + floatValue).takeLast(100) }
                    1 -> _emgDataD1.update { (it + floatValue).takeLast(100) }
                    2 -> _emgDataD2.update { (it + floatValue).takeLast(100) }
                }
                
                // Persist to database
                val username = _currentUsername.value
                val sessionId = _activeSessionId.value
                val label = _activeLabel.value

                if (username != null) {
                    viewModelScope.launch {
                        dataPointDao.insert(
                            DataPoint(
                                username = username,
                                timestamp = System.currentTimeMillis(),
                                value = floatValue,
                                type = "EMG",
                                sensorId = index,
                                sessionId = sessionId,
                                label = label
                            )
                        )
                    }
                }
            }
        }
    }

    fun startScanning() {
        bluetoothService?.startScanning()
    }

    fun stopScanning() {
        bluetoothService?.stopScanning()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bluetoothService?.connectToDevice(device.address)
    }

    fun disconnect() {
        bluetoothService?.disconnect()
    }

    fun sendCommand(command: String) {
        bluetoothService?.sendMessage(command.toByteArray())
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}
