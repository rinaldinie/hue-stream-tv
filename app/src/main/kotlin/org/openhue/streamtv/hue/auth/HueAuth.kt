package org.openhue.streamtv.hue.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.model.PairingState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HueAuth {
    private val TAG = "HueAuth"

    fun pairWithBridge(bridgeIp: String, bridgeId: String, timeoutSeconds: Int = 30): Flow<PairingState> = flow {
        emit(PairingState.WaitingForButton(timeoutSeconds))

        var secondsRemaining = timeoutSeconds
        var pairedCredentials: HueCredentials? = null

        while (secondsRemaining > 0 && pairedCredentials == null) {
            val response = sendPairRequest(bridgeIp)

            if (response != null) {
                if (response.has("success")) {
                    val success = response.getJSONObject("success")
                    val username = success.getString("username")
                    val clientKey = success.optString("clientkey", "")

                    if (clientKey.isNotEmpty()) {
                        pairedCredentials = HueCredentials(
                            bridgeIp = bridgeIp,
                            bridgeId = bridgeId,
                            username = username,
                            clientKey = clientKey
                        )
                        emit(PairingState.Success(pairedCredentials))
                        return@flow
                    }
                } else if (response.has("error")) {
                    val error = response.getJSONObject("error")
                    val type = error.optInt("type", 0)
                    // type 101 = link button not pressed
                    if (type != 101) {
                        emit(PairingState.Error(error.optString("description", "Pairing failed")))
                        return@flow
                    }
                }
            }

            delay(1000L)
            secondsRemaining--
            emit(PairingState.WaitingForButton(secondsRemaining))
        }

        if (pairedCredentials == null) {
            emit(PairingState.Error("Tempo scaduto. Premi il pulsante centrale sul Bridge prima che il timer scada."))
        }
    }.flowOn(Dispatchers.IO)

    private fun sendPairRequest(bridgeIp: String): JSONObject? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$bridgeIp/api")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            val payload = JSONObject().apply {
                put("devicetype", "hue_stream_tv#shield")
                put("generateclientkey", true)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val jsonArray = JSONArray(jsonStr)
                if (jsonArray.length() > 0) {
                    return jsonArray.getJSONObject(0)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Pairing request error: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return null
    }
}
