package org.openhue.streamtv.hue.stream

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.tls.DTLSClientProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DatagramTransport
import org.bouncycastle.tls.UDPTransport
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.repository.HueRepository
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

sealed class StreamState {
    object Idle : StreamState()
    object Connecting : StreamState()
    object Streaming : StreamState()
    object Paused : StreamState()
    data class Error(val message: String) : StreamState()
}

class HueStreamManager(
    private val repository: HueRepository
) {
    private val TAG = "HueStreamManager"
    private val PORT = 2100

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private var socket: DatagramSocket? = null
    private var dtlsTransport: DTLSTransport? = null
    private var activeCreds: HueCredentials? = null
    private var activeGroupId: String? = null

    private var keepAliveJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + Job())
    private var lastSendTime = 0L
    private var lastFrame: List<LightRgbColor> = emptyList()
    private val streamMutex = Mutex()

    suspend fun startStream(creds: HueCredentials, groupId: String): Boolean = withContext(Dispatchers.IO) {
        streamMutex.withLock {
            if (_streamState.value is StreamState.Streaming) {
                Log.d(TAG, "Stream already running")
                return@withLock true
            }

            _streamState.value = StreamState.Connecting
            activeCreds = creds
            activeGroupId = groupId

            if (creds.username == "demo-token" || creds.bridgeIp == "127.0.0.1") {
                Log.i(TAG, "Demo mode active: simulating Hue Entertainment stream")
                _streamState.value = StreamState.Streaming
                return@withLock true
            }

            try {
                // 1. Activate streaming on Hue Bridge via REST API
                Log.d(TAG, "Activating entertainment group $groupId via REST...")
                val activated = repository.setStreamActive(creds, groupId, true)
                if (!activated) {
                    Log.w(TAG, "Failed to activate group $groupId on bridge")
                    _streamState.value = StreamState.Error("Failed to activate Hue Entertainment area")
                    return@withLock false
                }

                // Brief delay for the bridge to open UDP port 2100
                delay(150)

                // 2. Open UDP Datagram Socket to Bridge:2100
                Log.d(TAG, "Connecting UDP socket to ${creds.bridgeIp}:$PORT...")
                val bridgeAddr = InetAddress.getByName(creds.bridgeIp)
                val udpSocket = DatagramSocket()
                udpSocket.connect(bridgeAddr, PORT)
                udpSocket.soTimeout = 3000
                socket = udpSocket

                // 3. Initiate DTLS 1.2 PSK Handshake via Bouncy Castle
                val transport: DatagramTransport = UDPTransport(udpSocket, 1500)
                val dtlsProtocol = DTLSClientProtocol()
                val dtlsClient = HueDtlsClient(creds.username, creds.clientKey)

                Log.d(TAG, "Starting DTLS handshake with PSK...")
                val secureTransport = dtlsProtocol.connect(dtlsClient, transport)
                dtlsTransport = secureTransport

                _streamState.value = StreamState.Streaming
                Log.i(TAG, "Hue Entertainment DTLS connected successfully!")

                startKeepAlive()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish DTLS connection: ${e.message}", e)
                _streamState.value = StreamState.Error(e.message ?: "DTLS Connection error")
                cleanup()
                false
            }
        }
    }

    /**
     * Send an RGB frame to the Hue Bridge over the established DTLS channel.
     */
    @Synchronized
    fun sendFrame(lights: List<LightRgbColor>) {
        if (_streamState.value !is StreamState.Streaming) return
        val transport = dtlsTransport ?: return

        try {
            lastFrame = lights
            val packet = HuePacketBuilder.buildPacket(lights)
            transport.send(packet, 0, packet.size)
            lastSendTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame: ${e.message}")
        }
    }

    /**
     * Pause streaming (e.g. when app loses focus or is not whitelisted).
     * Optionally releases entertainment area so room lights return to normal.
     */
    suspend fun pauseStream(releaseLights: Boolean = true) = withContext(Dispatchers.IO) {
        streamMutex.withLock {
            if (_streamState.value !is StreamState.Streaming && _streamState.value !is StreamState.Connecting) return@withLock
            Log.i(TAG, "Pausing Hue Stream (releaseLights=$releaseLights)...")

            _streamState.value = StreamState.Paused
            keepAliveJob?.cancel()

            if (releaseLights) {
                cleanup()
                val creds = activeCreds
                val groupId = activeGroupId
                if (creds != null && groupId != null) {
                    repository.setStreamActive(creds, groupId, false)
                }
            }
        }
    }

    /**
     * Resume streaming after being paused.
     */
    suspend fun resumeStream(): Boolean {
        val creds = activeCreds ?: repository.getSavedCredentials() ?: return false
        val groupId = activeGroupId ?: repository.getSelectedGroupId() ?: return false
        return startStream(creds, groupId)
    }

    /**
     * Completely stop streaming and release lights on bridge.
     */
    suspend fun stopStream() = withContext(Dispatchers.IO) {
        streamMutex.withLock {
            Log.i(TAG, "Stopping Hue Stream...")
            _streamState.value = StreamState.Idle
            keepAliveJob?.cancel()
            cleanup()

            val creds = activeCreds
            val groupId = activeGroupId
            if (creds != null && groupId != null) {
                repository.setStreamActive(creds, groupId, false)
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = managerScope.launch {
            while (isActive) {
                delay(1000)
                if (_streamState.value is StreamState.Streaming) {
                    val now = System.currentTimeMillis()
                    if (now - lastSendTime >= 1000 && lastFrame.isNotEmpty()) {
                        // Send last known frame to keep bridge streaming channel alive
                        sendFrame(lastFrame)
                    }
                }
            }
        }
    }

    private fun cleanup() {
        try {
            dtlsTransport?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing DTLS transport: ${e.message}")
        }
        dtlsTransport = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
    }
}
