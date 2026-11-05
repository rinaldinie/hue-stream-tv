package org.openhue.streamtv.hue.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.openhue.streamtv.hue.model.HueBridge
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class HueDiscovery(private val context: Context) {
    private val TAG = "HueDiscovery"

    suspend fun discoverAll(): List<HueBridge> = withContext(Dispatchers.IO) {
        val discovered = ConcurrentHashMap<String, HueBridge>()

        // N-UPnP cloud discovery fallback
        try {
            val cloudBridges = fetchCloudDiscovery()
            for (bridge in cloudBridges) {
                discovered[bridge.ip] = bridge
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud discovery failed: ${e.message}")
        }

        // SSDP UPnP Multicast
        try {
            val ssdpBridges = searchSsdp()
            for (bridge in ssdpBridges) {
                discovered[bridge.ip] = bridge
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP discovery failed: ${e.message}")
        }

        // Fetch bridge config and friendly names
        val verifiedList = mutableListOf<HueBridge>()
        for ((_, bridge) in discovered) {
            val verified = verifyBridge(bridge.ip)
            if (verified != null) {
                verifiedList.add(verified)
            }
        }

        verifiedList
    }

    private fun fetchCloudDiscovery(): List<HueBridge> {
        val result = mutableListOf<HueBridge>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://discovery.meethue.com/")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val ip = obj.optString("internalipaddress", "")
                    if (ip.isNotEmpty()) {
                        result.add(HueBridge(id = id, ip = ip, name = "Hue Bridge ($ip)"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error in cloud discovery: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return result
    }

    private fun searchSsdp(): List<HueBridge> {
        val result = mutableListOf<HueBridge>()
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("HueDiscoveryMulticast")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()

            socket = DatagramSocket()
            socket.soTimeout = 2000

            val ssdpMsg = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"

            val sendData = ssdpMsg.toByteArray()
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName("239.255.255.250"),
                1900
            )
            socket.send(sendPacket)

            val recvBuf = ByteArray(2048)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2000) {
                try {
                    val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                    socket.receive(recvPacket)
                    val response = String(recvPacket.data, 0, recvPacket.length)
                    if (response.contains("IpBridge", ignoreCase = true) || response.contains("hue-bridgeid", ignoreCase = true)) {
                        val ip = recvPacket.address.hostAddress ?: continue
                        result.add(HueBridge(id = "", ip = ip, name = "Hue Bridge ($ip)"))
                    }
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SSDP search exception: ${e.message}")
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return result
    }

    suspend fun verifyBridge(ip: String): HueBridge? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$ip/api/config")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val json = org.json.JSONObject(jsonStr)
                val bridgeId = json.optString("bridgeid", "")
                val name = json.optString("name", "Philips Hue Bridge")
                val modelId = json.optString("modelid", "")
                return@withContext HueBridge(
                    id = bridgeId,
                    ip = ip,
                    name = name,
                    modelId = modelId
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bridge verification failed for $ip: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        null
    }
}
