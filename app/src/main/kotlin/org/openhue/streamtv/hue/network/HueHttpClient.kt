package org.openhue.streamtv.hue.network

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HueHttpClient {
    private const val TAG = "HueHttpClient"

    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        }
    )

    private val sslSocketFactory: SSLSocketFactory by lazy {
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, SecureRandom())
        sc.socketFactory
    }

    private val permissiveHostnameVerifier = HostnameVerifier { _, _ -> true }

    fun openConnection(urlStr: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = sslSocketFactory
            conn.hostnameVerifier = permissiveHostnameVerifier
        }
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        return conn
    }

    fun executeGetWithFallback(bridgeIp: String, path: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): String? {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        
        try {
            val response = executeGet("https://$bridgeIp$cleanPath", connectTimeout, readTimeout)
            if (response != null) return response
        } catch (e: Exception) {
            logD(TAG, "HTTPS GET failed for $bridgeIp$cleanPath (${e.message}), trying HTTP fallback")
        }

        try {
            return executeGet("http://$bridgeIp$cleanPath", connectTimeout, readTimeout)
        } catch (e: Exception) {
            logW(TAG, "HTTP fallback GET also failed for $bridgeIp$cleanPath: ${e.message}")
        }
        return null
    }

    fun executeGet(urlStr: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(urlStr, connectTimeout, readTimeout)
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else {
                null
            }
        } finally {
            conn?.disconnect()
        }
    }

    fun executePostWithFallback(bridgeIp: String, path: String, payload: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): String? {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        
        try {
            val response = executePost("https://$bridgeIp$cleanPath", payload, connectTimeout, readTimeout)
            if (response != null) return response
        } catch (e: Exception) {
            logD(TAG, "HTTPS POST failed for $bridgeIp$cleanPath (${e.message}), trying HTTP fallback")
        }

        try {
            return executePost("http://$bridgeIp$cleanPath", payload, connectTimeout, readTimeout)
        } catch (e: Exception) {
            logW(TAG, "HTTP fallback POST also failed for $bridgeIp$cleanPath: ${e.message}")
        }
        return null
    }

    fun executePost(urlStr: String, payload: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(urlStr, connectTimeout, readTimeout)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload)
                it.flush()
            }

            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else {
                null
            }
        } finally {
            conn?.disconnect()
        }
    }

    fun executePutWithFallback(bridgeIp: String, path: String, payload: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): Boolean {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        
        try {
            val success = executePut("https://$bridgeIp$cleanPath", payload, connectTimeout, readTimeout)
            if (success) return true
        } catch (e: Exception) {
            logD(TAG, "HTTPS PUT failed for $bridgeIp$cleanPath (${e.message}), trying HTTP fallback")
        }

        try {
            return executePut("http://$bridgeIp$cleanPath", payload, connectTimeout, readTimeout)
        } catch (e: Exception) {
            logW(TAG, "HTTP fallback PUT also failed for $bridgeIp$cleanPath: ${e.message}")
        }
        return false
    }

    fun executePut(urlStr: String, payload: String, connectTimeout: Int = 3000, readTimeout: Int = 3000): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(urlStr, connectTimeout, readTimeout)
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload)
                it.flush()
            }

            conn.responseCode == 200
        } finally {
            conn?.disconnect()
        }
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Exception) {}
    }

    private fun logW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Exception) {}
    }
}
