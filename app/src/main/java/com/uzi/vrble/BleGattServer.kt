package com.uzi.vrble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import java.util.UUID

class BleGattServer(private val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var fileData: ByteArray? = null
    private var currentOffset = 0

    // UUIDs for custom BLE service
    companion object {
        // Custom Service UUID (generate your own or use this)
        private val SERVICE_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        
        // Characteristics UUIDs
        private val FILE_NAME_CHAR_UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
        private val FILE_DATA_CHAR_UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
        private val FILE_SIZE_CHAR_UUID = UUID.fromString("00001237-0000-1000-8000-00805f9b34fb")
        private val CONTROL_CHAR_UUID = UUID.fromString("00001238-0000-1000-8000-00805f9b34fb")
        
        // Client Characteristic Configuration Descriptor UUID (standard)
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        private const val TAG = "BleGattServer"
        private const val CHUNK_SIZE = 20 // BLE max payload is typically 20 bytes
    }

    fun initialize(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        return if (bluetoothAdapter != null) {
            Log.d(TAG, "Bluetooth adapter initialized")
            true
        } else {
            Log.e(TAG, "Bluetooth adapter not available")
            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startGattServer(): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattServer = bluetoothManager.openGattServer(context, GattServerCallback())
            
            // Create and add the service
            gattServer?.addService(createFileTransferService())
            
            Log.d(TAG, "GATT Server started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT Server: ${e.message}")
            false
        }
    }

    private fun createFileTransferService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // File Name Characteristic (Read)
        val fileNameChar = BluetoothGattCharacteristic(
            FILE_NAME_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(fileNameChar)

        // File Size Characteristic (Read)
        val fileSizeChar = BluetoothGattCharacteristic(
            FILE_SIZE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(fileSizeChar)

        // File Data Characteristic (Read + Notify)
        val fileDataChar = BluetoothGattCharacteristic(
            FILE_DATA_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Add CCCD for notifications
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        fileDataChar.addDescriptor(cccd)
        service.addCharacteristic(fileDataChar)

        // Control Characteristic (Write)
        val controlChar = BluetoothGattCharacteristic(
            CONTROL_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(controlChar)

        return service
    }

    fun loadFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return false
            }
            
            fileData = file.readBytes()
            currentOffset = 0
            Log.d(TAG, "File loaded: ${file.name}, size: ${fileData?.size} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file: ${e.message}")
            false
        }
    }

    fun loadFileFromAssets(assetFileName: String): Boolean {
        return try {
            val inputStream = context.assets.open(assetFileName)
            fileData = inputStream.readBytes()
            inputStream.close()
            currentOffset = 0
            Log.d(TAG, "File loaded from assets: $assetFileName, size: ${fileData?.size} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file from assets: ${e.message}")
            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopGattServer() {
        try {
            gattServer?.close()
            Log.d(TAG, "GATT Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT Server: ${e.message}")
        }
    }

    inner class GattServerCallback : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: android.bluetooth.BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "Connection state changed: ${device.address} status=$status newState=$newState")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Read request for characteristic: ${characteristic.uuid}")
            
            when (characteristic.uuid) {
                FILE_NAME_CHAR_UUID -> {
                    val fileName = "test_file.txt"
                    characteristic.value = fileName.toByteArray()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
                }
                FILE_SIZE_CHAR_UUID -> {
                    val size = fileData?.size ?: 0
                    characteristic.value = size.toString().toByteArray()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
                }
                FILE_DATA_CHAR_UUID -> {
                    if (fileData != null) {
                        val chunk = fileData!!.copyOfRange(currentOffset, minOf(currentOffset + CHUNK_SIZE, fileData!!.size))
                        currentOffset += chunk.size
                        characteristic.value = chunk
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                    } else {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                }
                else -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "Write request for characteristic: ${characteristic.uuid}")
            
            when (characteristic.uuid) {
                CONTROL_CHAR_UUID -> {
                    val command = String(value)
                    Log.d(TAG, "Control command: $command")
                    
                    when (command) {
                        "START" -> {
                            currentOffset = 0
                            Log.d(TAG, "Transfer started")
                        }
                        "RESET" -> {
                            currentOffset = 0
                            fileData = null
                            Log.d(TAG, "Reset completed")
                        }
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                }
                else -> {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "Descriptor write request: ${descriptor.uuid}")
            
            if (descriptor.uuid == CCCD_UUID) {
                Log.d(TAG, "Notifications enabled/disabled")
            }
            
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onNotificationSent(device: android.bluetooth.BluetoothDevice, status: Int) {
            Log.d(TAG, "Notification sent to ${device.address}, status: $status")
        }
    }
}
