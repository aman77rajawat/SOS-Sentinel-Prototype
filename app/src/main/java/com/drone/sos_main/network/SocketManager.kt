
package com.drone.sos_main.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {

    private const val TAG = "SocketManager"

    // for Emulator
    //private const val SERVER_URL = "http://10.0.2.2:5000"

    // Real device example:
    //private const val SERVER_URL = "http://192.168.1.13:5000"

    //deployed on this server
    private const val SERVER_URL = "https://sos-sentinel-prototype-1.onrender.com"
    private const val USERNAME = "TestUser"

    private var socket: Socket? = null

    // ─── Callbacks ─────────────────────────────────────────
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    var onSosSent: (() -> Unit)? = null
    var onSosAccepted: ((String) -> Unit)? = null
    var onDroneArrived: ((String) -> Unit)? = null
    var onSosCompleted: (() -> Unit)? = null
    var onSosCancelledByServer: (() -> Unit)? = null

    // ─── CONNECT ───────────────────────────────────────────
    fun connect() {
        try {

            val options = IO.Options.builder()
                .setReconnection(true)
                .setReconnectionAttempts(5)
                .setReconnectionDelay(2000)
                .setTransports(arrayOf("websocket"))
                .build()

            socket = IO.socket(SERVER_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Connected")
                registerUser()
                onConnected?.invoke()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Disconnected")
                onDisconnected?.invoke()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) {
                onDisconnected?.invoke()
            }

            // ─── Backend events ───────────────────────────

            socket?.on("sos-sent") {
                Log.d(TAG, "SOS confirmed")
                onSosSent?.invoke()
            }

            socket?.on("sos-accepted") { args ->
                val data = args[0] as? JSONObject
                val name = data?.optString("droneName", "Drone") ?: "Drone"
                onSosAccepted?.invoke(name)
            }

            socket?.on("drone-arrived") { args ->
                val data = args[0] as? JSONObject
                val name = data?.optString("droneName", "Drone") ?: "Drone"
                onDroneArrived?.invoke(name)
            }

            socket?.on("sos-completed") {
                onSosCompleted?.invoke()
            }

            socket?.on("sos-cancelled") {
                onSosCancelledByServer?.invoke()
            }

            socket?.connect()

        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Socket error")
        }
    }

    // ─── REGISTER USER ─────────────────────────────────────
    private fun registerUser() {
        val data = JSONObject().apply {
            put("username", USERNAME)
            put("lat", 0.0)
            put("lon", 0.0)
            put("alt", 0.0)
        }
        socket?.emit("register-user", data)
    }

    // ─── SEND SOS ──────────────────────────────────────────
    fun sendSos(lat: Double, lon: Double, alt: Double) {
        val data = JSONObject().apply {
            put("username", USERNAME)
            put("lat", lat)
            put("lon", lon)
            put("alt", alt)
            put("message", "Emergency assistance needed")
        }
        socket?.emit("send-sos", data)
    }

    // ─── LOCATION UPDATE ───────────────────────────────────
    fun updateLocation(lat: Double, lon: Double, alt: Double) {
        val data = JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("alt", alt)
        }
        socket?.emit("update-location", data)
    }

    fun cancelSos() {
        socket?.emit("cancel-sos")
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun isConnected(): Boolean = socket?.connected() == true
}
