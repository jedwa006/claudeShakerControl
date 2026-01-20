package com.shakercontrol.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages BLE scanning, connection, and GATT operations.
 */
@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequenceNumber = AtomicInteger(0)

    // Connection state
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    // Scanned devices
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    // Scanning state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Connected device info
    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    // Current RSSI for connected device
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    // Disconnect events for UI notification
    private val _disconnectEvents = MutableSharedFlow<DisconnectEvent>(replay = 0)
    val disconnectEvents: SharedFlow<DisconnectEvent> = _disconnectEvents.asSharedFlow()

    // Track if disconnect was user-initiated
    private var userInitiatedDisconnect = false

    // Track last connected address for reconnection
    private var lastConnectedAddress: String? = null

    // Incoming data channels
    private val _telemetryChannel = Channel<TelemetryParser.TelemetrySnapshot>(Channel.BUFFERED)
    val telemetryFlow: Flow<TelemetryParser.TelemetrySnapshot> = _telemetryChannel.receiveAsFlow()

    private val _eventChannel = Channel<EventParser.Event>(Channel.BUFFERED)
    val eventFlow: Flow<EventParser.Event> = _eventChannel.receiveAsFlow()

    private val _ackChannel = Channel<CommandAckParser.CommandAck>(Channel.BUFFERED)
    val ackFlow: Flow<CommandAckParser.CommandAck> = _ackChannel.receiveAsFlow()

    // Device info from GATT read
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    // GATT connection
    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    // Pending ACK tracking
    private val pendingAcks = mutableMapOf<Short, CompletableDeferred<CommandAckParser.CommandAck>>()

    /**
     * Check if Bluetooth is available and enabled.
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Start scanning for BLE devices with the expected service UUID.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        if (bluetoothAdapter == null || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not available or not enabled")
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            _isScanning.value = false
            return
        }

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "Scan started with service filter")

            // Auto-stop scan after timeout
            scope.launch {
                delay(SCAN_TIMEOUT_MS)
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _isScanning.value = false
        }
    }

    /**
     * Stop scanning for BLE devices.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
        _isScanning.value = false
        Log.d(TAG, "Scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            @SuppressLint("MissingPermission")
            val name = device.name ?: "Unknown"
            val rssi = result.rssi

            val scannedDevice = ScannedDevice(
                address = device.address,
                name = name,
                rssi = rssi
            )

            // Add or update device in list
            val current = _scannedDevices.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                current[existingIndex] = scannedDevice
            } else {
                current.add(scannedDevice)
            }
            _scannedDevices.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    // Hint name for reconnection (when gatt.device.name is null)
    private var pendingDeviceNameHint: String? = null

    /**
     * Connect to a BLE device by address.
     * @param nameHint Optional device name to use if gatt.device.name is null (for reconnection)
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String, nameHint: String? = null) {
        if (_connectionState.value != BleConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            Log.e(TAG, "Failed to get remote device")
            return
        }

        userInitiatedDisconnect = false
        lastConnectedAddress = address
        pendingDeviceNameHint = nameHint
        _connectionState.value = BleConnectionState.CONNECTING
        Log.d(TAG, "Connecting to $address (hint: $nameHint)")

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    /**
     * Disconnect from the current BLE device.
     * @param userInitiated If true, this was requested by the user and won't trigger auto-reconnect
     */
    @SuppressLint("MissingPermission")
    fun disconnect(userInitiated: Boolean = true) {
        userInitiatedDisconnect = userInitiated
        if (userInitiated) {
            lastConnectedAddress = null
        }
        bluetoothGatt?.let { gatt ->
            Log.d(TAG, "Disconnecting (user initiated: $userInitiated)")
            gatt.disconnect()
        }
    }

    /**
     * Get the last connected device address for reconnection.
     */
    fun getLastConnectedAddress(): String? = lastConnectedAddress

    /**
     * Request RSSI reading from connected device.
     * The result will be emitted via the rssi StateFlow.
     */
    @SuppressLint("MissingPermission")
    fun readRssi(): Boolean {
        return bluetoothGatt?.readRemoteRssi() ?: false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server, requesting MTU")
                    // Request larger MTU for telemetry frames (default 20 is too small)
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    cleanup()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu (status=$status)")
            _connectionState.value = BleConnectionState.DISCOVERING_SERVICES
            gatt.discoverServices()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _rssi.value = rssi
                Log.d(TAG, "RSSI: $rssi dBm")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                disconnect()
                return
            }

            Log.d(TAG, "Services discovered")
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "System Control Service not found")
                disconnect()
                return
            }

            // Get characteristics
            commandCharacteristic = service.getCharacteristic(BleConstants.CHAR_COMMAND_RX)
            val telemetryChar = service.getCharacteristic(BleConstants.CHAR_TELEMETRY_STREAM)
            val eventsAcksChar = service.getCharacteristic(BleConstants.CHAR_EVENTS_ACKS)

            if (commandCharacteristic == null || telemetryChar == null || eventsAcksChar == null) {
                Log.e(TAG, "Required characteristics not found")
                disconnect()
                return
            }

            // Enable notifications
            _connectionState.value = BleConnectionState.ENABLING_NOTIFICATIONS

            scope.launch {
                try {
                    enableNotifications(gatt, telemetryChar)
                    delay(100)
                    enableNotifications(gatt, eventsAcksChar)
                    delay(100)

                    // Read device info
                    val deviceInfoChar = service.getCharacteristic(BleConstants.CHAR_DEVICE_INFO)
                    if (deviceInfoChar != null) {
                        Log.d(TAG, "Reading device info characteristic...")
                        val result = gatt.readCharacteristic(deviceInfoChar)
                        Log.d(TAG, "readCharacteristic result: $result")
                    } else {
                        Log.w(TAG, "Device info characteristic not found")
                    }

                    _connectionState.value = BleConnectionState.CONNECTED
                    @SuppressLint("MissingPermission")
                    val deviceName = gatt.device.name ?: pendingDeviceNameHint ?: "Unknown"
                    _connectedDevice.value = ConnectedDevice(
                        address = gatt.device.address,
                        name = deviceName
                    )
                    pendingDeviceNameHint = null
                    Log.d(TAG, "Connection setup complete (name: $deviceName)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable notifications", e)
                    disconnect()
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            when (characteristic.uuid) {
                BleConstants.CHAR_DEVICE_INFO -> {
                    Log.d(TAG, "Device info read: ${value.size} bytes, data=${value.joinToString(" ") { "%02X".format(it) }}")
                    val info = DeviceInfo.parse(value)
                    if (info != null) {
                        Log.d(TAG, "Parsed device info: proto=${info.protocolVersion}, fw=${info.firmwareVersionString}, build=${info.buildId}, caps=0x${info.capabilityBits.toString(16)}")
                        _deviceInfo.value = info
                    } else {
                        Log.w(TAG, "Failed to parse device info")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingData(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Legacy callback for API < 33
            @Suppress("DEPRECATION")
            handleIncomingData(characteristic.uuid, characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)

        val cccd = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (cccd != null) {
            val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }
    }

    private fun handleIncomingData(charUuid: java.util.UUID, data: ByteArray) {
        Log.d(TAG, "Received ${data.size} bytes from $charUuid: ${data.joinToString(" ") { "%02X".format(it) }}")
        val frame = WireProtocol.decodeFrame(data)
        if (frame == null) {
            Log.w(TAG, "Failed to decode frame from ${charUuid}, size=${data.size}")
            return
        }
        Log.d(TAG, "Decoded frame: ver=${frame.protoVer}, type=${frame.msgType}, seq=${frame.seq}, payload=${frame.payload.size}B")

        when (frame.msgType) {
            MessageType.TELEMETRY_SNAPSHOT -> {
                val snapshot = TelemetryParser.parse(frame.payload)
                if (snapshot != null) {
                    scope.launch {
                        _telemetryChannel.send(snapshot)
                    }
                }
            }
            MessageType.COMMAND_ACK -> {
                val ack = CommandAckParser.parse(frame.payload)
                if (ack != null) {
                    // Complete pending deferred if any
                    pendingAcks.remove(ack.ackedSeq)?.complete(ack)

                    scope.launch {
                        _ackChannel.send(ack)
                    }
                }
            }
            MessageType.EVENT -> {
                val event = EventParser.parse(frame.payload)
                if (event != null) {
                    scope.launch {
                        _eventChannel.send(event)
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown message type: ${frame.msgType}")
            }
        }
    }

    /**
     * Send a command and wait for ACK.
     */
    suspend fun sendCommand(
        cmdId: Short,
        payload: ByteArray = ByteArray(0),
        timeoutMs: Long = 5000
    ): CommandAckParser.CommandAck? {
        val gatt = bluetoothGatt ?: return null
        val char = commandCharacteristic ?: return null

        if (_connectionState.value != BleConnectionState.CONNECTED) {
            return null
        }

        val seq = sequenceNumber.incrementAndGet().toShort()
        val frame = WireProtocol.encodeCommand(seq, cmdId, 0, payload)

        val deferred = CompletableDeferred<CommandAckParser.CommandAck>()
        pendingAcks[seq] = deferred

        return try {
            withContext(Dispatchers.IO) {
                @SuppressLint("MissingPermission")
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        char,
                        frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    char.value = frame
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }

                if (!writeResult) {
                    pendingAcks.remove(seq)
                    return@withContext null
                }

                withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command", e)
            pendingAcks.remove(seq)
            null
        }
    }

    /**
     * Send a command without waiting for ACK (fire and forget).
     */
    @SuppressLint("MissingPermission")
    fun sendCommandAsync(cmdId: Short, payload: ByteArray = ByteArray(0)) {
        val gatt = bluetoothGatt ?: return
        val char = commandCharacteristic ?: return

        if (_connectionState.value != BleConnectionState.CONNECTED) return

        val seq = sequenceNumber.incrementAndGet().toShort()
        val frame = WireProtocol.encodeCommand(seq, cmdId, 0, payload)

        scope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        char,
                        frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    char.value = frame
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send async command", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        val wasConnected = _connectionState.value == BleConnectionState.CONNECTED
        val deviceAddress = _connectedDevice.value?.address
        val deviceName = _connectedDevice.value?.name

        bluetoothGatt?.close()
        bluetoothGatt = null
        commandCharacteristic = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDevice.value = null
        _rssi.value = null
        pendingAcks.clear()

        // Emit disconnect event if we were previously connected
        if (wasConnected && deviceAddress != null) {
            scope.launch {
                _disconnectEvents.emit(
                    DisconnectEvent(
                        address = deviceAddress,
                        name = deviceName ?: "Unknown",
                        wasUserInitiated = userInitiatedDisconnect
                    )
                )
            }
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopScan()
        disconnect()
        scope.cancel()
    }
}

/**
 * BLE connection state.
 */
enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    DISCOVERING_SERVICES,
    ENABLING_NOTIFICATIONS,
    CONNECTED
}

/**
 * Scanned BLE device.
 */
data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int
)

/**
 * Connected BLE device.
 */
data class ConnectedDevice(
    val address: String,
    val name: String
)

/**
 * Event emitted when a BLE device disconnects.
 */
data class DisconnectEvent(
    val address: String,
    val name: String,
    val wasUserInitiated: Boolean
)
