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
import com.nudge.app.ai.Features
import com.nudge.app.ai.GestureModel
import com.nudge.app.ai.GestureSmoother
import com.nudge.app.ai.ModelProtocol
import com.nudge.app.ai.ModelRepository
import com.nudge.app.ai.SavedModel
import com.nudge.app.data.DataPoint
import com.nudge.app.data.DataPointDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class DeviceStatus { Disconnected, Connecting, Connected, Demo }

/** Where the user's trained gesture model is. */
enum class ModelSync {
    /** The user hasn't trained one yet */
    NoModel,
    /** Saved on the phone; will be sent when the wearable connects */
    NotConnected,
    Sending,
    OnDevice,
    /** Sending failed a few times; the user can retry */
    Failed,
    /** The wearable's firmware predates on-device training */
    FirmwareTooOld,
    /** The model was trained on a different sensor setup (e.g. one MyoWare vs the 8-channel strip) */
    WrongSensors
}

/** One reading from the wearable: one value per EMG channel plus the device's gesture guess. */
data class EmgSample(val timestamp: Long, val values: FloatArray, val gesture: String)

val GESTURES = listOf("REST", "OPEN", "PINCH", "CLOSE")

/** Gesture index as sent by the firmware, named with the installed model's labels. */
fun gestureFromIndex(index: Int, classes: List<String> = GestureModel.FIRMWARE_CLASSES): String =
    classes.getOrNull(index) ?: "UNKNOWN"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataPointDao: DataPointDao,
    private val models: ModelRepository
) : ViewModel() {

    private var bluetoothService: BluetoothLeService? = null
    private var isBound = false
    private var collectionJob: Job? = null
    private var demoJob: Job? = null
    private var scanTimeoutJob: Job? = null

    private val _currentUsername = MutableStateFlow<String?>(null)
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    private val _activeLabel = MutableStateFlow<String?>(null)

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    private val _demoMode = MutableStateFlow(false)
    val demoMode = _demoMode.asStateFlow()

    val deviceStatus: StateFlow<DeviceStatus> = combine(_connectionState, _demoMode) { state, demo ->
        when {
            demo -> DeviceStatus.Demo
            state == BluetoothProfile.STATE_CONNECTED -> DeviceStatus.Connected
            state == BluetoothProfile.STATE_CONNECTING -> DeviceStatus.Connecting
            else -> DeviceStatus.Disconnected
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceStatus.Disconnected)

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName = _connectedName.asStateFlow()

    // Recent history per channel, for the live charts
    private val _channels = MutableStateFlow<List<List<Float>>>(emptyList())
    /** Recent history per channel, oldest first. Its size is the wearable's channel count. */
    val channels = _channels.asStateFlow()

    /** Channels the connected wearable sends: 1 for the rev2 board's MyoWare, 8 for the 16-pad strip. Null until data arrives. */
    private val _deviceChannels = MutableStateFlow<Int?>(null)
    val deviceChannels = _deviceChannels.asStateFlow()

    /** Battery 0-100 reported by the main board; null if the wearable doesn't report it. */
    private val _battery = MutableStateFlow<Int?>(null)
    val battery = _battery.asStateFlow()

    /** Raw force-sensor readings (thumb, index), 0-4095; null if not reported. */
    private val _grip = MutableStateFlow<Pair<Int, Int>?>(null)
    val grip = _grip.asStateFlow()

    private val _lastGesture = MutableStateFlow("UNKNOWN")
    val lastGesture = _lastGesture.asStateFlow()

    private val _samples = MutableSharedFlow<EmgSample>(extraBufferCapacity = 256)
    /** Every reading as it arrives. Used by AI training to collect data. */
    val samples = _samples.asSharedFlow()

    private var lastMessageId = -1
    private val pendingPoints = mutableListOf<DataPoint>()

    /** The current user's saved model, if they've trained one. */
    val userModel: StateFlow<SavedModel?> = _currentUsername.flatMapLatest { user ->
        if (user == null) flowOf(null) else models.observe(user)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Model id the wearable reports: null before any data arrives, -1 if its firmware doesn't report one
    private val _deviceModelId = MutableStateFlow<Int?>(null)
    private val _upload = MutableStateFlow(ModelSync.NotConnected)
    private val _retry = MutableStateFlow(0)

    val modelSync: StateFlow<ModelSync> = combine(
        userModel, deviceStatus, _deviceModelId, _upload, _deviceChannels
    ) { model, status, deviceId, upload, channels ->
        when {
            model == null -> ModelSync.NoModel
            status != DeviceStatus.Connected && status != DeviceStatus.Demo -> ModelSync.NotConnected
            channels != null && channels != model.model.channels -> ModelSync.WrongSensors
            deviceId == model.id -> ModelSync.OnDevice
            deviceId == -1 -> ModelSync.FirmwareTooOld
            upload == ModelSync.Failed -> ModelSync.Failed
            else -> ModelSync.Sending
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModelSync.NoModel)

    // Demo device state
    private var demoInstalledId = 0
    private var demoPendingId = 0

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val s = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService = s
            isBound = true

            collectionJob?.cancel()
            collectionJob = viewModelScope.launch {
                launch { s.discoveredDevices.collect { _discoveredDevices.value = it } }
                launch { s.connectionState.collect { _connectionState.value = it } }
                launch { s.connectedName.collect { _connectedName.value = it } }
                launch { s.receivedPackets.collect { handlePacket(it) } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    init {
        context.bindService(Intent(context, BluetoothLeService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        // Write recorded points in batches instead of one insert per reading
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                flushPoints()
            }
        }

        // Forget what the last wearable had installed once it's gone
        viewModelScope.launch {
            deviceStatus.collect {
                if (it == DeviceStatus.Disconnected) {
                    _deviceModelId.value = null
                    _deviceChannels.value = null
                    _battery.value = null
                    _grip.value = null
                }
            }
        }

        // Whenever the wearable doesn't have this user's latest model, send it
        viewModelScope.launch {
            combine(userModel, _deviceModelId, _deviceChannels, _retry) { model, deviceId, channels, _ -> Triple(model, deviceId, channels) }
                .collectLatest { (model, deviceId, channels) ->
                    if (model == null || deviceId == null || deviceId < 0 || deviceId == model.id) return@collectLatest
                    // A model for a different sensor setup would be rejected by the wearable anyway
                    if (channels != model.model.channels) return@collectLatest
                    sendModel(model)
                }
        }
    }

    private suspend fun sendModel(saved: SavedModel) {
        repeat(UPLOAD_ATTEMPTS) { attempt ->
            _upload.value = ModelSync.Sending
            Log.i("BluetoothViewModel", "Sending gesture model ${saved.id} to wearable (attempt ${attempt + 1})")
            ModelProtocol.uploadCommands(saved.model, saved.id).forEach(::sendCommand)
            // The wearable confirms by reporting the new id in its readings
            val installed = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { _deviceModelId.first { it == saved.id } }
            if (installed != null) {
                _upload.value = ModelSync.OnDevice
                return
            }
        }
        _upload.value = ModelSync.Failed
    }

    /** Try sending the model again after it failed. */
    fun retryModelSync() {
        _upload.value = ModelSync.Sending
        _retry.value++
    }

    fun setCurrentUser(username: String?) {
        _currentUsername.value = username
    }

    fun setActiveSession(sessionId: Long?) {
        _activeSessionId.value = sessionId
    }

    /** The gesture the user is currently being asked to make, if any. The demo device acts it out. */
    fun setPromptedGesture(gesture: String?) {
        _activeLabel.value = gesture
    }

    private fun handlePacket(packet: Packet) {
        if (packet.messageId == lastMessageId) return
        lastMessageId = packet.messageId

        // Layout: one float per channel, then the gesture (int16), then the model id (uint16)
        val values = packet.data.takeWhile { it is Float }.map { it as Float }
        if (values.isEmpty()) return
        val modelId = (packet.data.firstOrNull { it is UShort } as? UShort)?.toInt() ?: -1
        _deviceModelId.value = modelId
        // A one-sensor model has its own gesture list, so name the index with the installed model's
        val classes = userModel.value?.takeIf { it.id == modelId }?.model?.classes ?: GestureModel.FIRMWARE_CLASSES
        val gesture = packet.data.firstOrNull { it is Short }?.let { gestureFromIndex((it as Short).toInt(), classes) } ?: "UNKNOWN"
        // Main board extras after the model id: battery % (uint8), then thumb and index force (uint16)
        val batteryIndex = packet.data.indexOfFirst { it is UByte }
        _battery.value = (packet.data.getOrNull(batteryIndex) as? UByte)?.toInt()
        val forces = if (batteryIndex < 0) emptyList() else packet.data.drop(batteryIndex + 1).filterIsInstance<UShort>()
        _grip.value = if (forces.size >= 2) forces[0].toInt() to forces[1].toInt() else null
        onSample(EmgSample(System.currentTimeMillis(), values.toFloatArray(), gesture))
    }

    private fun onSample(sample: EmgSample) {
        val n = sample.values.size
        _deviceChannels.value = n
        _channels.update { old ->
            val history = if (old.size == n) old else List(n) { emptyList() }
            history.mapIndexed { i, ch -> (ch + sample.values[i]).takeLast(HISTORY) }
        }
        _lastGesture.value = sample.gesture
        _samples.tryEmit(sample)

        // Only keep readings that belong to a therapy session
        val username = _currentUsername.value ?: return
        val sessionId = _activeSessionId.value ?: return
        synchronized(pendingPoints) {
            sample.values.forEachIndexed { i, v ->
                pendingPoints += DataPoint(
                    username = username,
                    timestamp = sample.timestamp,
                    value = v,
                    type = "EMG",
                    sensorId = i,
                    sessionId = sessionId
                )
            }
        }
    }

    private suspend fun flushPoints() {
        val batch = synchronized(pendingPoints) {
            if (pendingPoints.isEmpty()) return
            pendingPoints.toList().also { pendingPoints.clear() }
        }
        try {
            dataPointDao.insertAll(batch)
        } catch (e: Exception) {
            Log.e("BluetoothViewModel", "Failed to save ${batch.size} points", e)
        }
    }

    fun startScanning() {
        _isScanning.value = true
        bluetoothService?.startScanning()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScanning()
        }
    }

    fun stopScanning() {
        _isScanning.value = false
        bluetoothService?.stopScanning()
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopDemo()
        stopScanning()
        bluetoothService?.connectToDevice(device.address)
    }

    fun disconnect() {
        stopDemo()
        bluetoothService?.disconnect()
    }

    fun sendCommand(command: String) {
        if (_demoMode.value) {
            demoCommand(command)
            return
        }
        bluetoothService?.sendMessage(command.toByteArray())
    }

    /** Fake wearable for trying the app without hardware. */
    fun startDemo() {
        bluetoothService?.disconnect()
        stopScanning()
        _demoMode.value = true
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            // A new demo session is like putting the wearable back on: sensors sit a little differently
            val simulator = DemoSimulator.randomPlacement()
            val window = ArrayDeque<FloatArray>()
            val smoother = GestureSmoother()
            while (isActive) {
                // While recording training data, act out whatever gesture is being asked for
                val reading = simulator.next(_activeLabel.value)
                window.addLast(reading.values)
                if (window.size > Features.WINDOW) window.removeFirst()

                // Classify on the "device" with whatever model was sent to it, like the firmware does
                val model = userModel.value?.takeIf { it.id == demoInstalledId && it.model.channels == reading.values.size }?.model
                val gesture = if (model != null && window.size == Features.WINDOW) {
                    val (index, probability) = model.predictWindow(window.toList())
                    smoother.update(index, probability).let { if (it < 0) "UNKNOWN" else model.classes[it] }
                } else {
                    "UNKNOWN"
                }
                _deviceModelId.value = demoInstalledId
                _battery.value = 87
                // Like the rev2 board: no force sensors, so no grip pressure
                _grip.value = null
                onSample(reading.copy(gesture = gesture))
                delay(1000L / DemoSimulator.RATE_HZ)
            }
        }
    }

    private fun demoCommand(command: String) {
        Log.d("BluetoothViewModel", "Demo device received: $command")
        when {
            command.startsWith("mdl_begin") -> demoPendingId = command.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
            command.startsWith("mdl_end") -> demoInstalledId = demoPendingId
            command == ModelProtocol.CLEAR -> demoInstalledId = 0
        }
    }

    fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
        _demoMode.value = false
        _deviceModelId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDemo()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    companion object {
        const val HISTORY = 150 // 3 seconds at 50Hz
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val UPLOAD_ATTEMPTS = 3
        private const val UPLOAD_TIMEOUT_MS = 6_000L
    }
}
