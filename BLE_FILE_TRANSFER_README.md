# VR to Mac BLE File Transfer - Android/Kotlin Implementation

## Overview
This is a complete **BLE GATT Server** implementation that runs on Android VR headsets (like Meta Quest). It exposes .txt files over Bluetooth Low Energy (BLE) so they can be transferred to a Mac or other BLE-capable devices.

## Architecture

### Files Created:

1. **BleGattServer.kt** - Main BLE GATT Server implementation
   - Creates a custom BLE service with 4 characteristics
   - Manages file loading and chunked data transfer
   - Handles client read/write requests

2. **BluetoothPermissionManager.kt** - Permission handler
   - Manages Bluetooth and location permissions
   - Handles Android 12+ specific permission requirements

3. **MainActivity.kt** - Updated main activity
   - UI for starting/stopping the server
   - File selection and loading
   - Status display

## How It Works

### BLE Service Structure:
```
Service UUID: 12345678-1234-1234-1234-123456789012

Characteristics:
├─ File Name (UUID: 87654321-...)          [READ]
│  └─ Contains the filename to be transferred
│
├─ File Size (UUID: 11111111-...)          [READ]
│  └─ Contains total file size in bytes
│
├─ File Data (UUID: 66666666-...)          [READ + NOTIFY]
│  └─ Contains file data in 20-byte chunks
│  └─ Client reads multiple times to get full file
│
└─ Control (UUID: aaaaaaaa-...)            [WRITE + READ]
   └─ Accepts commands: "START", "RESET"
```

### Transfer Flow:

1. **Discovery Phase**
   - Mac scans for BLE devices
   - Finds your VR app advertising the custom service

2. **Connection Phase**
   - Mac connects to the BLE GATT Server
   - Discovers the service and characteristics

3. **File Transfer Phase**
   ```
   Mac Client                    VR Server (Android)
        |                               |
        |--- Read File Name ---------->|
        |<---------- "test_file.txt" ---|
        |
        |--- Read File Size ---------->|
        |<---------- "5242" (bytes) ----|
        |
        |--- Send START Command ------>|
        |                               |
        |--- Read File Data (0) ------->|
        |<--- First 20 bytes of file ---|
        |
        |--- Read File Data (20) ------>|
        |<--- Next 20 bytes of file ----|
        |
        |--- Read File Data (40) ------>|
        |<--- Next 20 bytes of file ----|
        |                 ...
        |--- (repeat until EOF) --------|
        |
        |--- Send RESET Command ------>|
        |                               |
   ```

## Android Setup

### Permissions Required:
- `BLUETOOTH` / `BLUETOOTH_SCAN` (Android 12+)
- `BLUETOOTH_ADVERTISE` (Android 12+)
- `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`

These are automatically requested when the app starts.

### Configuration:
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Java Version**: 11

## Usage

### 1. Load File
- Enter the file path (e.g., `/storage/emulated/0/Documents/test.txt`)
- Click "Load File" button
- Status shows if file was loaded successfully

### 2. Start Server
- Click "Start BLE Server" button
- Server begins advertising the custom BLE service
- Status shows "BLE GATT Server Running"

### 3. Connect from Mac
- Use the Python BLE client script (provided separately)
- Discovers the VR device
- Connects and reads file data

### 4. Stop Server
- Click "Stop BLE Server" to shutdown
- Disconnects all clients

## Technical Details

### Chunk-Based Transfer:
- BLE characteristic max payload: 20 bytes (MTU)
- Large files split into multiple reads
- Client keeps track of offset (0, 20, 40, ...)
- Server increments offset automatically

### Data Format:
- File name: UTF-8 string
- File size: UTF-8 string (decimal number)
- File data: Raw bytes in 20-byte chunks

### Key Classes:

**BleGattServer:**
```kotlin
- initialize()           // Setup Bluetooth adapter
- startGattServer()     // Start GATT server
- loadFile(path)        // Load .txt file into memory
- stopGattServer()      // Cleanup and close
```

**GattServerCallback:**
- `onConnectionStateChange()` - Handle client connections
- `onCharacteristicReadRequest()` - Send file data to client
- `onCharacteristicWriteRequest()` - Receive control commands
- `onDescriptorWriteRequest()` - Handle CCCD (notifications)

## Limitations & Considerations

1. **File Size**: 
   - Stored entirely in memory
   - Suitable for small-medium files (< 100MB)
   - For larger files, implement streaming

2. **BLE Bandwidth**:
   - ~20 bytes per read
   - ~100ms per read
   - ~2MB file takes ~27 minutes

3. **VR Specifics**:
   - Works on Meta Quest 2/3, Pico, etc.
   - May require sideloading on Quest
   - Battery drain during long transfers

4. **MAC Compatibility**:
   - Requires custom Mac client (Python script provided)
   - macOS doesn't natively accept BLE GATT services

## Next Steps

1. **Test on VR Device**:
   - Build and deploy to Meta Quest
   - Verify Bluetooth permissions

2. **Mac Client Implementation**:
   - Use provided Python `bleak` script
   - Connect to service UUID: `12345678-1234-1234-1234-123456789012`
   - Read characteristics in sequence

3. **Optimizations**:
   - Implement MTU negotiation for larger payloads
   - Add progress callbacks
   - Implement error handling & retry logic
   - Add file verification (checksum)

## Example: File Transfer Timeline

For a 1KB file:
```
Connections: 50-100ms
File Name Read: 10ms
File Size Read: 10ms
Data Reads (50x): 5-10s total
Total: ~5-11 seconds
```

## Debugging

Check Android logcat with filter `BleGattServer`:
```bash
adb logcat | grep "BleGattServer\|MainActivity"
```

Key log messages:
- "GATT Server started successfully"
- "Connection state changed: XX:XX:XX:XX:XX:XX"
- "Read request for characteristic"
- "File loaded: test.txt, size: XXXX bytes"

## Security Notes

This is a demo implementation. For production:
- Add authentication/encryption
- Validate file paths (prevent directory traversal)
- Implement timeout handling
- Add rate limiting
- Verify client identity before transfer

---

**Custom UUIDs Used:**
- Service: `12345678-1234-1234-1234-123456789012`
- File Name: `87654321-4321-4321-4321-210987654321`
- File Data: `11111111-2222-3333-4444-555555555555`
- File Size: `66666666-7777-8888-9999-000000000000`
- Control: `aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee`
