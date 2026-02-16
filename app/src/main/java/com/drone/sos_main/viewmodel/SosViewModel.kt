

package com.drone.sos_main.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drone.sos_main.network.SocketManager
import com.drone.sos_main.service.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class SosUiState(
    val isSosActive: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val statusMessage: String = "Connecting...",
    val droneInfo: String? = null
)

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val locationManager = LocationManager(application)

    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState

    init {

        SocketManager.onConnected = {
            updateState {
                copy(
                    isConnected = true,
                    isConnecting = false,
                    statusMessage = "Connected — ready"
                )
            }
        }

        SocketManager.onDisconnected = {
            updateState {
                copy(
                    isConnected = false,
                    isConnecting = false,
                    statusMessage = "Disconnected"
                )
            }
        }

        // IMPORTANT → activate only after backend confirms
        SocketManager.onSosSent = {
            updateState {
                copy(
                    isSosActive = true,
                    statusMessage = "SOS Active — streaming...",
                    droneInfo = null
                )
            }
        }

        SocketManager.onSosAccepted = { drone ->
            updateState {
                copy(
                    droneInfo = "$drone is on the way!",
                    statusMessage = "Help is coming!"
                )
            }
        }

        SocketManager.onDroneArrived = { drone ->
            updateState {
                copy(
                    droneInfo = "drone has arrived!",
                    statusMessage = "Drone reached you!"
                )
            }
        }

        SocketManager.onSosCompleted = {
            locationManager.stopStreaming()
            updateState {
                copy(
                    isSosActive = false,
                    droneInfo = null,
                    statusMessage = "Mission completed"
                )
            }
        }

        SocketManager.onSosCancelledByServer = {
            locationManager.stopStreaming()
            updateState {
                copy(
                    isSosActive = false,
                    droneInfo = null,
                    statusMessage = "SOS cancelled"
                )
            }
        }

        updateState { copy(isConnecting = true) }
        SocketManager.connect()
    }

    // ─── TRIGGER SOS ───────────────────────────────

    //this is for current location

//    fun triggerSos() {
//
//        if (!SocketManager.isConnected()) {
//            updateState { copy(statusMessage = "Not connected") }
//            return
//        }
//
//        locationManager.startStreaming()
//
//        viewModelScope.launch {
//            delay(1000)
//
//            SocketManager.sendSos(
//                locationManager.lastLatitude,
//                locationManager.lastLongitude,
//                locationManager.lastAltitude
//            )
//
//            updateState {
//                copy(statusMessage = "Sending SOS...")
//            }
//        }
//    }



    //this is for live location
    fun triggerSos() {

        if (!SocketManager.isConnected()) {
            updateState { copy(statusMessage = "Not connected") }
            return
        }

        locationManager.startStreaming()

        viewModelScope.launch {

            // WAIT until GPS gets real value
            while (locationManager.lastLatitude == 0.0 &&
                locationManager.lastLongitude == 0.0) {
                delay(500)
            }

            SocketManager.sendSos(
                lat = locationManager.lastLatitude,
                lon = locationManager.lastLongitude,
                alt = locationManager.lastAltitude
            )

            updateState {
                copy(statusMessage = "Sending SOS...")
            }
        }
    }


    // ─── CANCEL SOS ────────────────────────────────
    fun cancelSos() {
        locationManager.stopStreaming()
        SocketManager.cancelSos()

        updateState {
            copy(
                isSosActive = false,
                droneInfo = null,
                statusMessage = "Ready to send SOS"
            )
        }
    }

    override fun onCleared() {
        locationManager.stopStreaming()
        SocketManager.disconnect()
        super.onCleared()
    }

    private fun updateState(update: SosUiState.() -> SosUiState) {
        _uiState.value = _uiState.value.update()
    }
}
