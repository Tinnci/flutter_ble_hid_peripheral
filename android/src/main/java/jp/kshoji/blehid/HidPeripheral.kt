package jp.kshoji.blehid

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import jp.kshoji.blehid.util.BleUuidUtils
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
// import java.util.function.Consumer // Java's Consumer, consider Kotlin's ((ConnectionState) -> Unit)?

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
abstract class HidPeripheral protected constructor(
    context: Context,
    private val needInputReport: Boolean,
    private val needOutputReport: Boolean,
    private val needFeatureReport: Boolean,
    private val dataSendingRate: Int
) {
    private val applicationContext: Context = context.applicationContext
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser?
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null
    private var gattServer: BluetoothGattServer? = null
    private val bluetoothDevicesMap = mutableMapOf<String, BluetoothDevice>()
    private var servicesToAdd: Queue<BluetoothGattService> = LinkedBlockingQueue()
    private var originalAdapterName: String? = null // Added to store original adapter name

    // --- Companion Object for static members & methods ---
    companion object {
        private val TAG = HidPeripheral::class.java.simpleName

        // Main items (converted to functions or const vals)
        fun INPUT(size: Int): Byte = (0x80 or size).toByte()
        fun OUTPUT(size: Int): Byte = (0x90 or size).toByte()
        fun COLLECTION(size: Int): Byte = (0xA0 or size).toByte()
        fun FEATURE(size: Int): Byte = (0xB0 or size).toByte()
        fun END_COLLECTION(size: Int): Byte = (0xC0 or size).toByte()

        fun USAGE_PAGE(size: Int): Byte = (0x04 or size).toByte()
        fun LOGICAL_MINIMUM(size: Int): Byte = (0x14 or size).toByte()
        fun LOGICAL_MAXIMUM(size: Int): Byte = (0x24 or size).toByte()
        fun PHYSICAL_MINIMUM(size: Int): Byte = (0x34 or size).toByte()
        fun PHYSICAL_MAXIMUM(size: Int): Byte = (0x44 or size).toByte()
        fun UNIT_EXPONENT(size: Int): Byte = (0x54 or size).toByte()
        fun UNIT(size: Int): Byte = (0x64 or size).toByte()
        fun REPORT_SIZE(size: Int): Byte = (0x74 or size).toByte()
        fun REPORT_ID(size: Int): Byte = (0x84 or size).toByte()
        fun REPORT_COUNT(size: Int): Byte = (0x94 or size).toByte()

        fun USAGE(size: Int): Byte = (0x08 or size).toByte()
        fun USAGE_MINIMUM(size: Int): Byte = (0x18 or size).toByte()
        fun USAGE_MAXIMUM(size: Int): Byte = (0x28 or size).toByte()

        fun LSB(value: Int): Byte = (value and 0xff).toByte()
        fun MSB(value: Int): Byte = (value shr 8 and 0xff).toByte()

        // UUIDs (as const val)
        val SERVICE_DEVICE_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x180A)
        val CHARACTERISTIC_MANUFACTURER_NAME: UUID = BleUuidUtils.fromShortValue(0x2A29)
        val CHARACTERISTIC_MODEL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A24)
        val CHARACTERISTIC_SERIAL_NUMBER: UUID = BleUuidUtils.fromShortValue(0x2A25)

        val SERVICE_BATTERY: UUID = BleUuidUtils.fromShortValue(0x180F)
        val CHARACTERISTIC_BATTERY_LEVEL: UUID = BleUuidUtils.fromShortValue(0x2A19)

        val SERVICE_BLE_HID: UUID = BleUuidUtils.fromShortValue(0x1812)
        val CHARACTERISTIC_HID_INFORMATION: UUID = BleUuidUtils.fromShortValue(0x2A4A)
        val CHARACTERISTIC_REPORT_MAP: UUID = BleUuidUtils.fromShortValue(0x2A4B)
        val CHARACTERISTIC_HID_CONTROL_POINT: UUID = BleUuidUtils.fromShortValue(0x2A4C)
        val CHARACTERISTIC_REPORT: UUID = BleUuidUtils.fromShortValue(0x2A4D)
        val CHARACTERISTIC_PROTOCOL_MODE: UUID = BleUuidUtils.fromShortValue(0x2A4E)

        val DESCRIPTOR_REPORT_REFERENCE: UUID = BleUuidUtils.fromShortValue(0x2908)
        val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION: UUID = BleUuidUtils.fromShortValue(0x2902)

        private val EMPTY_BYTES = ByteArray(0)
        private val RESPONSE_HID_INFORMATION = byteArrayOf(0x11, 0x01, 0x00, 0x03) // bCountryCode = 0 (Not Supported), Flags = RemoteWake, NormallyConnectable
    }

    var manufacturer: String = "kshoji.jp"
    var deviceName: String = "BLE HID Kotlin"
    var serialNumber: String = "00000000"

    protected abstract fun getReportMap(): ByteArray
    protected abstract fun onOutputReport(outputReport: ByteArray)

    private val inputReportQueue: Queue<ByteArray> = ConcurrentLinkedQueue()

    protected fun addInputReport(inputReport: ByteArray?) {
        if (inputReport != null && inputReport.isNotEmpty()) {
            inputReportQueue.offer(inputReport)
        }
    }

    data class ConnectionState(
        val device: BluetoothDevice,
        val status: Int,
        val newState: Int
    )

    var connectionStateCallback: ((ConnectionState) -> Unit)? = null

    private val bluetoothManager: BluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
    private var reportTimer: Timer? = null


    init {
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            throw UnsupportedOperationException("Bluetooth is not available.")
        }
        if (!bluetoothAdapter.isEnabled) {
            throw UnsupportedOperationException("Bluetooth is disabled.")
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // It can be null if BLE advertising is not supported,
            // though isBlePeripheralSupported should ideally catch this.
            Log.w(TAG, "Bluetooth LE Advertiser not supported by this device.")
            // Depending on strictness, could throw an exception here.
        }
    }

    private fun setupReportTimer() {
        reportTimer?.cancel() // Cancel any existing timer
        reportTimer = Timer()
        reportTimer?.schedule(object : TimerTask() {
            @SuppressLint("MissingPermission")
            override fun run() {
                handler.post {
                    var reportData: ByteArray? = null // Store report data for logging
                    try {
                        val data = inputReportQueue.poll()
                        reportData = data // Assign for logging even if processing fails later
                        
                        val currentDeviceMapSize = bluetoothDevicesMap.size

                        // Only log detailed polling status if there's actual data polled
                        if (data != null) {
                            Log.d(TAG, "ReportTimer: Polled data successfully. Queue size after poll: ${inputReportQueue.size}. Characteristic null? ${inputReportCharacteristic == null}. GattServer null? ${gattServer == null}. Connected devices in map: $currentDeviceMapSize")
                            if (currentDeviceMapSize > 0) {
                                Log.d(TAG, "ReportTimer: Devices in map for sending: ${bluetoothDevicesMap.keys.joinToString()}")
                            }
                        }
                        // Enhanced Logging End (Adjusted)

                        if (data != null && inputReportCharacteristic != null && gattServer != null) {
                            val currentDevices = getDevices() // Get devices only if we are going to send
                            Log.d(TAG, "Attempting to send Input Report: ${data.contentToString()} to ${currentDevices.size} device(s)")
                            inputReportCharacteristic!!.value = data
                            var notificationSent = false
                            if (currentDevices.isNotEmpty()) {
                                for (device in currentDevices) {
                                    Log.d(TAG, "ReportTimer: Checking device from getDevices(): ${device.address}, is it in current bluetoothDevicesMap? ${bluetoothDevicesMap.containsKey(device.address)}")
                                    if (gattServer!!.notifyCharacteristicChanged(device, inputReportCharacteristic!!, false)) {
                                        notificationSent = true
                                        Log.d(TAG, "notifyCharacteristicChanged success for device: ${device.address}")
                                    } else {
                                        Log.w(TAG, "notifyCharacteristicChanged failed for device: ${device.address}")
                                    }
                                }
                                if (!notificationSent) { // Check if any notification succeeded among connected devices
                                    Log.w(TAG, "No notifications successfully sent, though ${currentDevices.size} device(s) were targeted.")
                                }
                            } else {
                                Log.d(TAG, "No devices connected, report not sent: ${data.contentToString()}")
                            }
                        } else if (data != null) {
                            Log.w(TAG, "Input report polled but not sent (characteristic or server null): ${data.contentToString()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending input report (Report: ${reportData?.contentToString() ?: "null"}) ", e)
                    }
                }
            }
        }, dataSendingRate.toLong(), dataSendingRate.toLong())
    }


    private fun addService(service: BluetoothGattService) {
        servicesToAdd.add(service)
        if (servicesToAdd.size == 1) {
            handler.post {
                if (gattServer != null) {
                     try {
                        gattServer?.addService(servicesToAdd.peek()!!)
                     } catch (e: SecurityException) {
                         Log.e(TAG, "SecurityException when adding service. Ensure BLUETOOTH_CONNECT permission.", e)
                     }
                } else {
                    Log.w(TAG, "gattServer is null when trying to add service. Advertising might not have been started properly.")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setUpDeviceInformationService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        var characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_MANUFACTURER_NAME,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic) // Value set on read request

        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_MODEL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic) // Value set on read request
        
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_SERIAL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic) // Value set on read request
        return service
    }
    
    @SuppressLint("MissingPermission")
    private fun setUpBatteryService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_BATTERY_LEVEL,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Default value
        characteristic.value = byteArrayOf(100.toByte()) // 100%

        // CCCD for Battery Level
        val cccDescriptor = BluetoothGattDescriptor(
            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccDescriptor)
        service.addCharacteristic(characteristic)
        return service
    }

    @SuppressLint("MissingPermission")
    private fun setUpHidService(
        isNeedInputReport: Boolean,
        isNeedOutputReport: Boolean,
        isNeedFeatureReport: Boolean
    ): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_BLE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // HID Information
        var characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_HID_INFORMATION,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic) // Value set on read (RESPONSE_HID_INFORMATION)

        // Report Map
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_REPORT_MAP,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic) // Value set on read (getReportMap())

        // Protocol Mode (default to Report Protocol Mode)
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_PROTOCOL_MODE,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristic.value = byteArrayOf(0x01) // Report Protocol Mode
        service.addCharacteristic(characteristic)

        // HID Control Point (optional, for suspend/exit suspend)
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_HID_CONTROL_POINT,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)


        // Input Report
        if (isNeedInputReport) {
            inputReportCharacteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ // Write permission is not typical for host-side input report char
            )
            // CCCD for Input Report
            val cccDescriptor = BluetoothGattDescriptor(
                DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            inputReportCharacteristic!!.addDescriptor(cccDescriptor)

            // Report Reference for Input Report (ID 1, Type Input)
            val inputReportReference = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ
            )
            // Value will be set in onDescriptorReadRequest
            inputReportCharacteristic!!.addDescriptor(inputReportReference)
            service.addCharacteristic(inputReportCharacteristic)
        }

        // Output Report
        if (isNeedOutputReport) {
            val outputReportCharacteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            // Report Reference for Output Report (ID defined by device, e.g. 1, Type Output)
             val outputReportReference = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ
            )
            // Value will be set in onDescriptorReadRequest
            outputReportCharacteristic.addDescriptor(outputReportReference)
            service.addCharacteristic(outputReportCharacteristic)
        }

        // Feature Report
        if (isNeedFeatureReport) {
            val featureReportCharacteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
             val featureReportReference = BluetoothGattDescriptor(
                DESCRIPTOR_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ
            )
            // Value will be set in onDescriptorReadRequest
            featureReportCharacteristic.addDescriptor(featureReportReference)
            service.addCharacteristic(featureReportCharacteristic)
        }
        return service
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        handler.post {
            if (gattServer != null) {
                Log.w(TAG, "GATT Server already started. Stopping and restarting.")
                stopAdvertisingInternal() 
            }
            
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth not enabled or not available.")
                connectionStateCallback?.invoke(ConnectionState( DUMMY_DEVICE_FOR_ERROR , BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED))
                return@post
            }
            
            gattServer = bluetoothManager.openGattServer(applicationContext, gattServerCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server. Check BT permissions (BLUETOOTH_CONNECT) and availability.")
                connectionStateCallback?.invoke(ConnectionState( DUMMY_DEVICE_FOR_ERROR , BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED))
                return@post
            }

            servicesToAdd.clear()
            addService(setUpDeviceInformationService())
            addService(setUpBatteryService()) 
            addService(setUpHidService(needInputReport, needOutputReport, needFeatureReport))
        }
    }
    
    private val DUMMY_DEVICE_FOR_ERROR: BluetoothDevice by lazy {
        bluetoothAdapter!!.getRemoteDevice("00:11:22:33:44:55") // Placeholder for error reporting
    }


    @SuppressLint("MissingPermission")
    private fun startBleAdvertising() {
         val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser not available, cannot start advertising.")
            // No name change attempt yet, so no restore needed here directly unless originalAdapterName was set by a faulty previous attempt.
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Or ADVERTISE_MODE_BALANCED
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Or ADVERTISE_TX_POWER_HIGH
            .setConnectable(true)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Device name will be taken from BluetoothAdapter's name (which we'll temporarily set)
            .addServiceUuid(ParcelUuid(SERVICE_BLE_HID))
        
        val data = dataBuilder.build()
        
        // Scan Response (optional, can include more info like manufacturer data)
        val scanResponse = AdvertiseData.Builder()
            // .setIncludeTxPowerLevel(true) // Example
            .build()

        try {
            // Temporarily set the adapter name for advertising
            if (bluetoothAdapter != null) {
                originalAdapterName = bluetoothAdapter.name
                if (deviceName.isNotEmpty() && bluetoothAdapter.name != deviceName) {
                    Log.i(TAG, "Attempting to temporarily change adapter name from '$originalAdapterName' to '$deviceName' for advertising.")
                    if (bluetoothAdapter.setName(deviceName)) { // setName returns boolean success
                         Log.i(TAG, "Adapter name changed successfully to '$deviceName' for advertising.")
                    } else {
                         Log.w(TAG, "Failed to change adapter name to '$deviceName'. Advertising will use current adapter name: '${bluetoothAdapter.name}'.")
                         // If setName fails, originalAdapterName still holds the name before this attempt.
                         // We will attempt to restore to originalAdapterName regardless.
                    }
                }
            }

            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallbackInstance)
            setupReportTimer() // Call after advertising starts successfully - Moved to onStartSuccess or just below
            // Log moved to onStartSuccess for more accuracy
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on startAdvertising. Ensure BLUETOOTH_ADVERTISE/BLUETOOTH_CONNECT permission.", e)
            restoreAdapterName() // Restore name on failure
        } catch (e: Exception) {
            Log.e(TAG, "Exception on startAdvertising", e)
            restoreAdapterName() // Restore name on failure
        }
    }

    @SuppressLint("MissingPermission")
    private fun restoreAdapterName() {
        if (bluetoothAdapter != null && originalAdapterName != null) {
            if (bluetoothAdapter.name != originalAdapterName) {
                Log.i(TAG, "Attempting to restore adapter name from '${bluetoothAdapter.name}' to '$originalAdapterName'.")
                if (bluetoothAdapter.setName(originalAdapterName)) {
                    Log.i(TAG, "Adapter name restored successfully to '$originalAdapterName'.")
                } else {
                    Log.w(TAG, "Failed to restore adapter name to '$originalAdapterName'. Current adapter name: '${bluetoothAdapter.name}'.")
                }
            }
            originalAdapterName = null // Clear it after attempting/verifying restoration
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        handler.post {
            stopAdvertisingInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertisingInternal() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallbackInstance)
        } catch (e: SecurityException) {
             Log.e(TAG, "SecurityException on stopAdvertising. Ensure BLUETOOTH_ADVERTISE permission.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception on stopAdvertising", e)
        }
        
        try {
            gattServer?.close() // This might be too aggressive if we want to keep connections alive while merely stopping advertising
        } catch (e: Exception) {
            Log.e(TAG, "Exception on gattServer close", e)
        }
        gattServer = null
        // bluetoothDevicesMap.clear() // Let's test NOT clearing it here, or clearing it more strategically.
                                    // If a device is connected, and we stop advertising, we don't want to lose the device from map.
                                    // It's mainly cleared when a device explicitly disconnects or connection fails.
        servicesToAdd.clear()
        reportTimer?.cancel() // Stop the timer when advertising stops
        restoreAdapterName() // Restore adapter name when advertising stops
        Log.d(TAG, "Advertising stopped and GATT server closed. bluetoothDevicesMap size: ${bluetoothDevicesMap.size}")
    }

    private val advertiseCallbackInstance = object : AdvertiseCallback() {
        @SuppressLint("MissingPermission")
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started. Settings: $settingsInEffect. Effective advertised name: ${bluetoothAdapter?.name}")
            setupReportTimer() // <--- ADD Timer setup here
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
            var message = "Advertise failed: "
            when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> message += "Data too large."
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> message += "Too many advertisers."
                ADVERTISE_FAILED_ALREADY_STARTED -> message += "Already started."
                ADVERTISE_FAILED_INTERNAL_ERROR -> message += "Internal error."
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> message += "Feature unsupported."
                else -> message += "Unknown error."
            }
            Log.e(TAG, message)
            reportTimer?.cancel() // <--- ADD Timer cancellation here
            restoreAdapterName() // Restore name on advertising failure
            // Consider a callback to the plugin user about advertising failure
        }
    }
    
    private fun getDevices(): Set<BluetoothDevice> {
        return Collections.unmodifiableSet(HashSet(bluetoothDevicesMap.values))
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            val deviceAddress = device.address
            Log.d(TAG, "onConnectionStateChange START: $deviceAddress status: $status newState: $newState. Current map size: ${bluetoothDevicesMap.size}")

            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        bluetoothDevicesMap[deviceAddress] = device
                        Log.i(TAG, "Device connected: $deviceAddress. Total connected: ${bluetoothDevicesMap.size}")
                        // Stop advertising once a device is connected to allow only one connection (typical for HID)
                        try {
                            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallbackInstance)
                            Log.d(TAG, "Stopped advertising as device connected.")
                            restoreAdapterName() // Restore adapter name as advertising is stopped
                        } catch (se: SecurityException) {
                             Log.e(TAG, "SecurityException stopping advertising after connection. Check BLUETOOTH_ADVERTISE.", se)
                             // Even if stopping adv fails, try to restore name if it was changed.
                             restoreAdapterName()
                        }

                        // Optional: Initiate bonding if not already bonded
                        if (device.bondState == BluetoothDevice.BOND_NONE) {
                            Log.d(TAG, "Device $deviceAddress not bonded. Attempting to create bond.")
                            try {
                                device.createBond()
                            } catch (se: SecurityException) {
                                Log.e(TAG, "SecurityException on createBond for $deviceAddress. Check BLUETOOTH_CONNECT.", se)
                            }
                        }

                    } else {
                        bluetoothDevicesMap.remove(deviceAddress)
                        Log.i(TAG, "Connection failed for $deviceAddress with status: $status. Map size after removal: ${bluetoothDevicesMap.size}")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val removed = bluetoothDevicesMap.remove(deviceAddress)
                    Log.i(TAG, "Device disconnected: $deviceAddress. Removed from map: ${removed != null}. Map size after removal: ${bluetoothDevicesMap.size}")
                    // Restart advertising when a device disconnects so others can connect if it was stopped before
                    if (bluetoothDevicesMap.isEmpty()) { // Only restart if no other devices are connected
                        Log.d(TAG, "Last device disconnected, map is empty. Restarting advertising.")
                        startBleAdvertising() // This call might clear the map if not handled carefully
                    } else {
                        Log.d(TAG, "Device $deviceAddress disconnected, but other devices still connected (${bluetoothDevicesMap.size}). Not restarting advertising yet.")
                    }
                }
                connectionStateCallback?.invoke(ConnectionState(device, status, newState))
                Log.d(TAG, "onConnectionStateChange END for $deviceAddress. Current map size: ${bluetoothDevicesMap.size}")
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(TAG, "onCharacteristicReadRequest: UUID ${characteristic.uuid} offset $offset")
            handler.post {
                var valueToSent: ByteArray? = null
                var responseStatus = BluetoothGatt.GATT_SUCCESS

                when (characteristic.uuid) {
                    CHARACTERISTIC_HID_INFORMATION -> valueToSent = RESPONSE_HID_INFORMATION
                    CHARACTERISTIC_REPORT_MAP -> valueToSent = getReportMap()
                    CHARACTERISTIC_MANUFACTURER_NAME -> valueToSent = manufacturer.toByteArray(StandardCharsets.UTF_8)
                    CHARACTERISTIC_MODEL_NUMBER -> valueToSent = deviceName.toByteArray(StandardCharsets.UTF_8)
                    CHARACTERISTIC_SERIAL_NUMBER -> valueToSent = serialNumber.toByteArray(StandardCharsets.UTF_8)
                    CHARACTERISTIC_BATTERY_LEVEL -> valueToSent = byteArrayOf(100.toByte()) // Example, should be dynamic
                    CHARACTERISTIC_PROTOCOL_MODE -> valueToSent = characteristic.value ?: byteArrayOf(0x01) // Report mode
                    CHARACTERISTIC_REPORT -> {
                        // This is for reading an Input Report or Feature Report value if supported
                        // For Input report, it's usually notified, not read.
                        // If a specific report ID is expected:
                        // if (characteristic == inputReportCharacteristic) {
                        //    valueToSent = inputReportCharacteristic?.value ?: EMPTY_BYTES
                        // } else { // Handle other report types (e.g. feature) if they are readable
                           valueToSent = characteristic.value ?: EMPTY_BYTES
                        // }
                    }
                    else -> {
                        Log.w(TAG, "Unknown characteristic read request: ${characteristic.uuid}")
                        responseStatus = BluetoothGatt.GATT_READ_NOT_PERMITTED
                    }
                }

                var finalValue = valueToSent
                if (responseStatus == BluetoothGatt.GATT_SUCCESS && valueToSent != null) {
                    if (offset > 0) {
                        if (offset < valueToSent.size) {
                            finalValue = valueToSent.copyOfRange(offset, valueToSent.size)
                        } else {
                            finalValue = EMPTY_BYTES // Offset is beyond the value length
                        }
                    }
                } else if (responseStatus != BluetoothGatt.GATT_SUCCESS) {
                    finalValue = null
                }
                
                try {
                    gattServer?.sendResponse(device, requestId, responseStatus, 0, finalValue)
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException sending read response. Check BLUETOOTH_CONNECT.", se)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.d(TAG, "onDescriptorReadRequest: UUID ${descriptor.uuid} for char ${descriptor.characteristic.uuid} offset $offset")
            handler.post {
                var valueToSent: ByteArray? = null
                var responseStatus = BluetoothGatt.GATT_SUCCESS

                when (descriptor.uuid) {
                    DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION -> {
                        // Return current CCCD value for this client (device)
                        // This should be stored per device per characteristic if managed dynamically
                        // For simplicity, returning a default or last written value.
                        // A common default is notifications disabled.
                        valueToSent = descriptor.value ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        Log.d(TAG, "Reading CCCD for ${descriptor.characteristic.uuid}: value ${valueToSent.contentToString()}")
                    }
                    DESCRIPTOR_REPORT_REFERENCE -> {
                        // Determine which characteristic this descriptor belongs to
                        when (descriptor.characteristic.uuid) {
                            CHARACTERISTIC_REPORT -> {
                                // This needs to be specific to the report type (Input, Output, Feature)
                                // and its ID. The HidPeripheral class structure might need
                                // to store references to output/feature characteristics if they also use ID 1.
                                if (descriptor.characteristic == inputReportCharacteristic) {
                                    valueToSent = byteArrayOf(0x01, 0x01) // Report ID 1, Type Input (0x01)
                                } else {
                                    // Heuristic: if it's not input, and we need to provide a value,
                                    // this part would need more info on how Output/Feature reports are ID'd
                                    // in this library's design. Let's assume ID 1, Type Output (0x02) for now if it's an output report.
                                    // This part is a placeholder and needs to be accurate based on the HID spec and report map.
                                    val props = descriptor.characteristic.properties
                                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                         // Likely an Output report
                                        valueToSent = byteArrayOf(0x01, 0x02) // Report ID 1, Type Output (0x02)
                                    } else {
                                         // Likely a Feature report
                                        valueToSent = byteArrayOf(0x01, 0x03) // Report ID 1, Type Feature (0x03)
                                    }
                                }
                            }
                            else -> {
                                Log.w(TAG, "ReportReference read for unknown characteristic: ${descriptor.characteristic.uuid}")
                                responseStatus = BluetoothGatt.GATT_FAILURE
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown descriptor read request: ${descriptor.uuid}")
                        responseStatus = BluetoothGatt.GATT_READ_NOT_PERMITTED
                    }
                }
                
                var finalValue = valueToSent
                if (responseStatus == BluetoothGatt.GATT_SUCCESS && valueToSent != null) {
                     if (offset > 0) {
                        if (offset < valueToSent.size) {
                            finalValue = valueToSent.copyOfRange(offset, valueToSent.size)
                        } else {
                            finalValue = EMPTY_BYTES
                        }
                    }
                } else if (responseStatus != BluetoothGatt.GATT_SUCCESS) {
                    finalValue = null
                }
                
                try {
                    gattServer?.sendResponse(device, requestId, responseStatus, 0, finalValue)
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException sending descriptor read response. Check BLUETOOTH_CONNECT.", se)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            val incomingValue = value ?: EMPTY_BYTES
            Log.d(TAG, "onCharacteristicWriteRequest: UUID ${characteristic.uuid} value: ${incomingValue.contentToString()}")
            
            var status = BluetoothGatt.GATT_SUCCESS
            handler.post {
                when (characteristic.uuid) {
                    CHARACTERISTIC_REPORT -> { // Output Report from host or Feature Report set by host
                         // Distinguish if it's Output or Feature based on characteristic instance if possible
                         // For now, assume it's an Output Report if it's writable this way
                        onOutputReport(incomingValue)
                        characteristic.value = incomingValue // Update the characteristic's local value if it's also readable
                    }
                    CHARACTERISTIC_PROTOCOL_MODE -> {
                        if (incomingValue.isNotEmpty()) {
                            characteristic.value = incomingValue // Update local value
                            Log.d(TAG, "Protocol Mode Set to: ${incomingValue[0]}") // 0x00 = Boot, 0x01 = Report
                        } else {
                            status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                        }
                    }
                    CHARACTERISTIC_HID_CONTROL_POINT -> {
                        if (incomingValue.isNotEmpty()) {
                            when(incomingValue[0]) {
                                0x00.toByte() -> Log.d(TAG, "HID Control Point: Suspend")
                                0x01.toByte() -> Log.d(TAG, "HID Control Point: Exit Suspend")
                                else -> Log.w(TAG, "Unknown HID Control Point command: ${incomingValue[0]}")
                            }
                        } else {
                             status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                        }
                    }
                    else -> {
                        Log.w(TAG, "Write to unhandled characteristic: ${characteristic.uuid}")
                        status = BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                    }
                }

                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, status, offset, if (status == BluetoothGatt.GATT_SUCCESS) incomingValue else null)
                    } catch (se: SecurityException) {
                         Log.e(TAG, "SecurityException sending write response. Check BLUETOOTH_CONNECT.", se)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            val incomingValue = value ?: EMPTY_BYTES
            Log.d(TAG, "onDescriptorWriteRequest: UUID ${descriptor.uuid} for char ${descriptor.characteristic.uuid} value: ${incomingValue.contentToString()}")
            var status = BluetoothGatt.GATT_SUCCESS
            
            handler.post {
                if (DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION == descriptor.uuid) {
                    if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, incomingValue)) {
                        Log.d(TAG, "Notifications enabled for ${descriptor.characteristic.uuid} by ${device.address}")
                        descriptor.value = incomingValue // Persist the CCCD value
                    } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, incomingValue)) {
                        Log.d(TAG, "Notifications disabled for ${descriptor.characteristic.uuid} by ${device.address}")
                        descriptor.value = incomingValue // Persist the CCCD value
                    } else {
                        Log.w(TAG, "Invalid CCCD value: ${incomingValue.contentToString()}")
                        status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                    }
                } else {
                    Log.w(TAG, "Write to unhandled descriptor: ${descriptor.uuid}")
                    status = BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                }

                if (responseNeeded) {
                     try {
                        gattServer?.sendResponse(device, requestId, status, offset, if (status == BluetoothGatt.GATT_SUCCESS) incomingValue else null)
                    } catch (se: SecurityException) {
                         Log.e(TAG, "SecurityException sending descriptor write response. Check BLUETOOTH_CONNECT.", se)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.d(TAG, "onServiceAdded: ${service.uuid} status: $status")
            servicesToAdd.poll() 
            if (servicesToAdd.isNotEmpty()) {
                if (gattServer != null) {
                    try {
                        gattServer?.addService(servicesToAdd.peek()!!)
                    } catch (se: SecurityException) {
                         Log.e(TAG, "SecurityException onServiceAdded when adding next service. Check BLUETOOTH_CONNECT.", se)
                    }
                } else {
                     Log.e(TAG, "GattServer null when trying to add next service in onServiceAdded.")
                }
            } else {
                Log.d(TAG, "All services added. Starting BLE advertising.")
                startBleAdvertising()
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            Log.d(TAG, "MTU changed to $mtu for device: ${device?.address}")
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                 Log.w(TAG, "Notification sent failure for ${device?.address}, status: $status")
            }
        }
    }
} 