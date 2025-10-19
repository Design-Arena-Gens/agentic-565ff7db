package com.example.rccar

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

private const val TAG = "RC_CAR"
private const val HC05_NAME = "HC-05"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class RcCarUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastCommand: Char? = null,
    val commandHistory: List<String> = emptyList(),
    val bondedDevices: List<String> = emptyList(),
    val hasPermissions: Boolean = false,
    val showRetrySnackbar: Boolean = false
)

enum class ConnectionState {
    Connecting, Connected, Disconnected
}

@SuppressLint("MissingPermission")
class RcCarViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    private val _uiState = MutableStateFlow(RcCarUiState())
    val uiState: StateFlow<RcCarUiState> = _uiState.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnecting = false
    private var reconnectAttempts = 0

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            findAndConnect()
        }
    }

    fun findAndConnect() {
        if (isConnecting || _uiState.value.connectionState == ConnectionState.Connected) return
        isConnecting = true
        reconnectAttempts = 0

        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
            while (reconnectAttempts < 3 && _uiState.value.connectionState != ConnectionState.Connected) {
                try {
                    val hc05 = findHc05Device()
                    if (hc05 != null) {
                        connectToDevice(hc05)
                        _uiState.update { it.copy(connectionState = ConnectionState.Connected) }
                        isConnecting = false
                        return@launch
                    } else {
                        Log.w(TAG, "HC-05 not found in bonded devices.")
                        updateBondedDevices()
                        reconnectAttempts++
                        delay(5000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection attempt ${reconnectAttempts + 1} failed", e)
                    closeConnection()
                    reconnectAttempts++
                    delay(5000)
                }
            }
            _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
            isConnecting = false
        }
    }

    private fun findHc05Device(): BluetoothDevice? {
        if (!PermissionHelper.hasPermissions(getApplication())) {
            Log.w(TAG, "Cannot search for devices, permissions not granted.")
            return null
        }
        return bluetoothAdapter?.bondedDevices?.find { HC05_NAME in (it.name ?: "") }
    }

    private suspend fun connectToDevice(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Log.i(TAG, "Connected to ${device.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Could not connect to device", e)
            closeConnection()
            throw e
        }
    }

    fun sendCommand(command: Char) {
        if (_uiState.value.connectionState != ConnectionState.Connected) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(command.code)
                outputStream?.flush()
                Log.d(TAG, "Sent command: $command")
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        val newHistory = (listOf("'$command'") + it.commandHistory).take(10)
                        it.copy(lastCommand = command, commandHistory = newHistory)
                    }
                }
                _uiState.update { it.copy(showRetrySnackbar = false) }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send command: $command", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(showRetrySnackbar = true) }
                }
                handleConnectionLoss()
            }
        }
    }

    private fun handleConnectionLoss() {
        if (_uiState.value.connectionState == ConnectionState.Connected) {
            closeConnection()
            _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
            findAndConnect() // Start auto-reconnect
        }
    }

    fun closeConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connection", e)
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(showRetrySnackbar = false) }
    }

    private fun updateBondedDevices() {
        if (PermissionHelper.hasPermissions(getApplication())) {
            val devices = bluetoothAdapter?.bondedDevices?.map { it.name ?: "Unknown Device" } ?: emptyList()
            _uiState.update { it.copy(bondedDevices = devices) }
        }
    }

    override fun onCleared() {
        sendCommand('S') // Fail-safe
        closeConnection()
        super.onCleared()
    }
}
