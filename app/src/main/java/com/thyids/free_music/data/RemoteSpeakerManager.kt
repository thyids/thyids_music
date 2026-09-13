package com.thyids.free_music.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class RemoteSpeakerManager(
    private val context: Context,
    private val onCommandReceived: (JSONObject) -> Unit,
    private val onConnectionStatusChanged: (Boolean, String?) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val serverClients = ConcurrentHashMap.newKeySet<Socket>()
    private var clientSocket: Socket? = null
    private var out: PrintWriter? = null
    private var heartbeatJob: Job? = null
    private var clientConnectionJob: Job? = null
    @Volatile
    private var isServerRunning = false
    @Volatile
    private var isClientConnected = false
    @Volatile
    private var reconnectEnabled = false
    private var lastServerIp: String? = null
    private var discoveryRunning = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var connectionLocksAcquired = false
    
    private val PORT = 8899
    private val DISCOVERY_PORT = 8898
    private val CONNECT_TIMEOUT_MS = 5000
    private val HANDSHAKE_TIMEOUT_MS = 1500
    private val HEARTBEAT_INTERVAL_MS = 4000L
    private val CLIENT_READ_TIMEOUT_MS = (HEARTBEAT_INTERVAL_MS * 3).toInt()
    private val PROTOCOL_VERSION = 2
    private val TAG = "RemoteSpeaker"

    // --- Discovery ---

    fun startDiscovery(onSpeakerFound: (String) -> Unit) {
        if (discoveryRunning) return
        discoveryRunning = true
        acquireMulticastLock()
        scope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                
                Log.d(TAG, "Discovery started...")
                while (true) {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message == "FREE_MUSIC_SPEAKER") {
                        val ip = packet.address.hostAddress
                        if (ip != null) {
                            withContext(Dispatchers.Main) {
                                onSpeakerFound(ip)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            } finally {
                discoveryRunning = false
                multicastLock?.release()
                multicastLock = null
            }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = runCatching {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.createMulticastLock("free_music_speaker_discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun acquireConnectionLocks() {
        if (connectionLocksAcquired) return
        connectionLocksAcquired = true

        runCatching {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager
                .createWifiLock(lockType, "free_music_speaker_connection")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
            Log.d(TAG, "Wi-Fi connection lock acquired")
        }.onFailure {
            Log.w(TAG, "Unable to acquire Wi-Fi lock", it)
        }

        runCatching {
            val powerManager = context.applicationContext
                .getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "free_music:remote_speaker")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
            Log.d(TAG, "CPU wake lock acquired")
        }.onFailure {
            Log.w(TAG, "Unable to acquire CPU wake lock", it)
        }
    }

    private fun releaseConnectionLocks() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w(TAG, "Unable to release Wi-Fi lock", it)
        }
        wifiLock = null

        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w(TAG, "Unable to release CPU wake lock", it)
        }
        wakeLock = null
        connectionLocksAcquired = false
    }

    private fun releaseConnectionLocksIfIdle() {
        if (!isServerRunning && !reconnectEnabled) {
            releaseConnectionLocks()
        }
    }

    private fun startBroadcasting() {
        scope.launch {
            val socket = DatagramSocket()
            socket.broadcast = true
            val message = "FREE_MUSIC_SPEAKER".toByteArray()
            val address = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(message, message.size, address, DISCOVERY_PORT)
            
            while (isServerRunning) {
                try {
                    socket.send(packet)
                    delay(2000)
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast error", e)
                }
            }
            socket.close()
        }
    }

    // --- Server Mode (Speaker) ---

    fun startServer() {
        if (isServerRunning) return
        isServerRunning = true
        acquireConnectionLocks()
        startBroadcasting()
        
        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                Log.d(TAG, "Server started on port $PORT")
                while (isServerRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                isServerRunning = false
                releaseConnectionLocksIfIdle()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        serverClients.forEach { previous ->
            if (previous !== socket) {
                runCatching { previous.close() }
            }
        }
        serverClients.add(socket)
        scope.launch {
            var serverHeartbeatJob: Job? = null
            try {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 0
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                serverHeartbeatJob = scope.launch {
                    while (isServerRunning && serverClients.contains(socket)) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        runCatching {
                            writer.println(
                                JSONObject().put("type", "PING").toString()
                            )
                        }.onFailure {
                            Log.w(TAG, "Server heartbeat failed", it)
                            break
                        }
                    }
                }
                while (isServerRunning) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    when (json.optString("type")) {
                        "HELLO" -> writer.println(
                            JSONObject()
                                .put("type", "HELLO_ACK")
                                .put("version", PROTOCOL_VERSION)
                                .toString()
                        )
                        "PING" -> writer.println(
                            JSONObject().put("type", "PONG").toString()
                        )
                        "PONG" -> Unit
                        else -> withContext(Dispatchers.Main) {
                            onCommandReceived(json)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isServerRunning) Log.w(TAG, "Handle client error: ${e.message}")
            } finally {
                serverHeartbeatJob?.cancel()
                serverClients.remove(socket)
                socket.close()
            }
        }
    }

    fun stopServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverClients.forEach { client ->
            runCatching { client.close() }
        }
        serverClients.clear()
        releaseConnectionLocksIfIdle()
    }

    // --- Client Mode (Controller) ---

    fun connectToServer(ip: String) {
        lastServerIp = ip
        reconnectEnabled = true
        acquireConnectionLocks()
        clientConnectionJob?.cancel()
        closeClientSocket()
        clientConnectionJob = scope.launch {
            var retryDelay = 1000L
            var firstAttempt = true
            while (reconnectEnabled && lastServerIp == ip) {
                val connected = runClientSession(ip)
                if (!reconnectEnabled || lastServerIp != ip) break

                if (connected || firstAttempt) {
                    withContext(Dispatchers.Main) {
                        onConnectionStatusChanged(false, null)
                    }
                }
                firstAttempt = false
                retryDelay = if (connected) 1000L else (retryDelay * 2).coerceAtMost(10000L)
                delay(retryDelay)
            }
        }
    }

    private suspend fun runClientSession(ip: String): Boolean {
        var socket: Socket? = null
        var connected = false
        try {
            val activeSocket = Socket().apply {
                tcpNoDelay = true
                keepAlive = true
                soTimeout = HANDSHAKE_TIMEOUT_MS
                connect(InetSocketAddress(ip, PORT), CONNECT_TIMEOUT_MS)
            }
            socket = activeSocket
            val activeWriter = PrintWriter(activeSocket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(activeSocket.getInputStream()))
            clientSocket = activeSocket
            out = activeWriter
            isClientConnected = true
            connected = true
            withContext(Dispatchers.Main) {
                onConnectionStatusChanged(true, ip)
            }

            heartbeatJob?.cancel()
            heartbeatJob = scope.launch {
                while (isClientConnected && clientSocket === activeSocket) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    runCatching {
                        activeWriter.println(JSONObject().put("type", "PING").toString())
                    }.onFailure {
                        Log.w(TAG, "Heartbeat failed", it)
                        break
                    }
                }
            }

            activeWriter.println(
                JSONObject()
                    .put("type", "HELLO")
                    .put("version", PROTOCOL_VERSION)
                    .toString()
            )
            var heartbeatResponseRequired = false
            try {
                val handshakeLine = reader.readLine()
                if (handshakeLine != null) {
                    val responseType = JSONObject(handshakeLine).optString("type")
                    heartbeatResponseRequired = responseType == "HELLO_ACK" || responseType == "PONG"
                    if (responseType == "PING") {
                        activeWriter.println(
                            JSONObject().put("type", "PONG").toString()
                        )
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Legacy speaker protocol detected")
            }
            activeSocket.soTimeout = if (heartbeatResponseRequired) {
                CLIENT_READ_TIMEOUT_MS
            } else {
                0
            }

            while (isClientConnected && reconnectEnabled) {
                val line = reader.readLine() ?: break
                val json = JSONObject(line)
                when (json.optString("type")) {
                    "PING" -> activeWriter.println(
                        JSONObject().put("type", "PONG").toString()
                    )
                    "PONG", "HELLO_ACK" -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            if (reconnectEnabled) {
                Log.w(TAG, "Server heartbeat timed out; reconnecting")
            }
        } catch (e: Exception) {
            if (reconnectEnabled) Log.w(TAG, "Connect error: ${e.message}")
        } finally {
            heartbeatJob?.cancel()
            heartbeatJob = null
            isClientConnected = false
            if (clientSocket === socket) {
                clientSocket = null
                out = null
            }
            runCatching { socket?.close() }
        }
        return connected
    }

    fun sendCommand(json: JSONObject) {
        scope.launch {
            try {
                val writer = out
                if (writer == null) {
                    Log.w(TAG, "Cannot send command: not connected")
                    return@launch
                }
                writer.println(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Send command error", e)
            }
        }
    }

    fun disconnect() {
        reconnectEnabled = false
        lastServerIp = null
        clientConnectionJob?.cancel()
        clientConnectionJob = null
        closeClientSocket()
        releaseConnectionLocks()
        scope.launch(Dispatchers.Main) {
            onConnectionStatusChanged(false, null)
        }
    }

    private fun closeClientSocket() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        isClientConnected = false
        runCatching { clientSocket?.close() }
        clientSocket = null
        out = null
    }

    fun shutdown() {
        reconnectEnabled = false
        lastServerIp = null
        clientConnectionJob?.cancel()
        clientConnectionJob = null
        closeClientSocket()
        stopServer()
        releaseConnectionLocks()
        scope.cancel()
    }
    
    fun isConnected() = isClientConnected
}
