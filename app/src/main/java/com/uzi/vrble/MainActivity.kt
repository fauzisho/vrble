package com.uzi.vrble

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uzi.vrble.ui.theme.VRBLETheme

class MainActivity : ComponentActivity() {

    private lateinit var bleGattServer: BleGattServer
    private lateinit var permissionManager: BluetoothPermissionManager
    
    private val serverStatusState = mutableStateOf("Idle")
    private val selectedFileState = mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            serverStatusState.value = "Permissions Granted - Ready to start server"
        } else {
            serverStatusState.value = "Some permissions denied"
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionManager = BluetoothPermissionManager(this)
        bleGattServer = BleGattServer(this)
        
        // Request permissions if needed
        if (!permissionManager.hasAllPermissions()) {
            permissionLauncher.launch(permissionManager.getMissingPermissions())
        } else {
            serverStatusState.value = "Permissions Granted - Ready to start server"
        }
        
        enableEdgeToEdge()
        setContent {
            VRBLETheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleServerUI(
                        modifier = Modifier.padding(innerPadding),
                        onStartServer = { onStartServer() },
                        onStopServer = { onStopServer() },
                        onLoadFile = { filePath -> onLoadFile(filePath) },
                        onLoadPlaceholder = { onLoadPlaceholder() },
                        statusState = serverStatusState,
                        selectedFileState = selectedFileState
                    )
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onStartServer() {
        if (!bleGattServer.initialize()) {
            serverStatusState.value = "Failed to initialize Bluetooth"
            return
        }
        
        if (!bleGattServer.startGattServer()) {
            serverStatusState.value = "Failed to start GATT Server"
            return
        }
        
        serverStatusState.value = "BLE GATT Server Running\nWaiting for connections..."
        Log.d(TAG, "BLE GATT Server started successfully")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onStopServer() {
        bleGattServer.stopGattServer()
        serverStatusState.value = "Server Stopped"
        Log.d(TAG, "BLE GATT Server stopped")
    }

    private fun onLoadFile(filePath: String) {
        if (filePath.isEmpty()) {
            serverStatusState.value = "Please enter a valid file path"
            return
        }
        
        if (bleGattServer.loadFile(filePath)) {
            selectedFileState.value = filePath
            serverStatusState.value = "File loaded successfully:\n$filePath"
        } else {
            serverStatusState.value = "Failed to load file"
        }
    }

    private fun onLoadPlaceholder() {
        if (bleGattServer.loadFileFromAssets("placeholder.txt")) {
            selectedFileState.value = "placeholder.txt (from assets)"
            serverStatusState.value = "Placeholder file loaded successfully!"
        } else {
            serverStatusState.value = "Failed to load placeholder file"
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun BleServerUI(
    modifier: Modifier = Modifier,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onLoadFile: (String) -> Unit,
    onLoadPlaceholder: () -> Unit,
    statusState: androidx.compose.runtime.MutableState<String>,
    selectedFileState: androidx.compose.runtime.MutableState<String>
) {
    val filePathState = remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VR BLE File Transfer Server")
        
        Text("Status: ${statusState.value}")
        
        TextField(
            value = filePathState.value,
            onValueChange = { filePathState.value = it },
            label = { Text("File Path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Button(
            onClick = { onLoadFile(filePathState.value) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load File from Path")
        }
        
        Button(
            onClick = { onLoadPlaceholder() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load Placeholder (Assets)")
        }
        
        if (selectedFileState.value.isNotEmpty()) {
            Text("Selected: ${selectedFileState.value}")
        }
        
        Button(
            onClick = onStartServer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start BLE Server")
        }
        
        Button(
            onClick = onStopServer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop BLE Server")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BleServerUIPreview() {
    VRBLETheme {
        BleServerUI(
            onStartServer = {},
            onStopServer = {},
            onLoadFile = {},
            onLoadPlaceholder = {},
            statusState = remember { mutableStateOf("Idle") },
            selectedFileState = remember { mutableStateOf("") }
        )
    }
}
