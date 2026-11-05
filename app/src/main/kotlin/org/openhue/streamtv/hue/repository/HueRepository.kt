package org.openhue.streamtv.hue.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.model.HueGroup
import org.openhue.streamtv.hue.model.LightLocation
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HueRepository(private val context: Context) {
    private val TAG = "HueRepository"
    private val PREF_NAME = "hue_stream_tv_prefs"
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getSavedCredentials(): HueCredentials? {
        val ip = prefs.getString("bridge_ip", null) ?: return null
        val id = prefs.getString("bridge_id", "") ?: ""
        val user = prefs.getString("bridge_username", null) ?: return null
        val clientKey = prefs.getString("bridge_client_key", null) ?: return null
        return HueCredentials(bridgeIp = ip, bridgeId = id, username = user, clientKey = clientKey)
    }

    fun saveCredentials(creds: HueCredentials) {
        prefs.edit()
            .putString("bridge_ip", creds.bridgeIp)
            .putString("bridge_id", creds.bridgeId)
            .putString("bridge_username", creds.username)
            .putString("bridge_client_key", creds.clientKey)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove("bridge_ip")
            .remove("bridge_id")
            .remove("bridge_username")
            .remove("bridge_client_key")
            .remove("selected_group_id")
            .apply()
    }

    fun getSelectedGroupId(): String? {
        return prefs.getString("selected_group_id", null)
    }

    fun setSelectedGroupId(groupId: String) {
        prefs.edit().putString("selected_group_id", groupId).apply()
    }

    fun getBrightnessPercent(): Int {
        return prefs.getInt("brightness_percent", 100)
    }

    fun setBrightnessPercent(percent: Int) {
        prefs.edit().putInt("brightness_percent", percent).apply()
    }

    fun getDynamicsName(): String {
        return prefs.getString("sync_dynamics", "BALANCED") ?: "BALANCED"
    }

    fun setDynamicsName(name: String) {
        prefs.edit().putString("sync_dynamics", name).apply()
    }

    fun isAutoReleaseEnabled(): Boolean {
        return prefs.getBoolean("auto_release_enabled", true)
    }

    fun setAutoReleaseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_release_enabled", enabled).apply()
    }

    suspend fun getEntertainmentGroups(creds: HueCredentials): List<HueGroup> = withContext(Dispatchers.IO) {
        if (creds.username == "demo-token" || creds.bridgeIp == "127.0.0.1") {
            return@withContext listOf(
                HueGroup(
                    id = "demo-area-1",
                    name = "Living Room TV (Simulation)",
                    type = "Entertainment",
                    lightIds = listOf("1", "2", "3", "4"),
                    locations = mapOf(
                        "1" to LightLocation(-0.8, 1.0, 0.4),  // Left
                        "2" to LightLocation(0.8, 1.0, 0.4),   // Right
                        "3" to LightLocation(-0.4, 1.0, -0.4), // Bottom Left
                        "4" to LightLocation(0.4, 1.0, -0.4)   // Bottom Right
                    )
                )
            )
        }
        val groups = mutableListOf<HueGroup>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://${creds.bridgeIp}/api/${creds.username}/groups")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val root = JSONObject(jsonStr)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val groupObj = root.getJSONObject(key)
                    val type = groupObj.optString("type", "")

                    if (type.equals("Entertainment", ignoreCase = true)) {
                        val name = groupObj.optString("name", "Area Entertainment #$key")
                        val lightsArray = groupObj.optJSONArray("lights")
                        val lightIds = mutableListOf<String>()
                        if (lightsArray != null) {
                            for (i in 0 until lightsArray.length()) {
                                lightIds.add(lightsArray.getString(i))
                            }
                        }

                        val locations = mutableMapOf<String, LightLocation>()
                        val locationsObj = groupObj.optJSONObject("locations")
                        if (locationsObj != null) {
                            val locKeys = locationsObj.keys()
                            while (locKeys.hasNext()) {
                                val lightId = locKeys.next()
                                val coords = locationsObj.optJSONArray(lightId)
                                if (coords != null && coords.length() >= 3) {
                                    locations[lightId] = LightLocation(
                                        x = coords.optDouble(0, 0.0),
                                        y = coords.optDouble(1, 0.0),
                                        z = coords.optDouble(2, 0.0)
                                    )
                                }
                            }
                        }

                        groups.add(
                            HueGroup(
                                id = key,
                                name = name,
                                type = type,
                                lightIds = lightIds,
                                locations = locations
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching groups: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        groups
    }

    suspend fun setStreamActive(creds: HueCredentials, groupId: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (creds.username == "demo-token" || creds.bridgeIp == "127.0.0.1") {
            return@withContext true
        }
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://${creds.bridgeIp}/api/${creds.username}/groups/$groupId")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            val payload = JSONObject().apply {
                val streamObj = JSONObject().apply {
                    put("active", active)
                }
                put("stream", streamObj)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext (conn.responseCode == 200)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting stream active=$active: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
