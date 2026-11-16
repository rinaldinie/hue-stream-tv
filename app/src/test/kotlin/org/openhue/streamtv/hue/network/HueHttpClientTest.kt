package org.openhue.streamtv.hue.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.HttpsURLConnection

class HueHttpClientTest {

    @Test
    fun testOpenConnectionHttpsConfiguresSsl() {
        val conn = HueHttpClient.openConnection("https://127.0.0.1/api/config", connectTimeout = 1000, readTimeout = 1000)
        assertTrue(conn is HttpsURLConnection)
        val httpsConn = conn as HttpsURLConnection
        assertNotNull(httpsConn.sslSocketFactory)
        assertNotNull(httpsConn.hostnameVerifier)
        assertTrue(httpsConn.hostnameVerifier.verify("127.0.0.1", null))
        assertTrue(httpsConn.hostnameVerifier.verify("ecb5fafffe21d034", null))
    }

    @Test
    fun testOpenConnectionHttp() {
        val conn = HueHttpClient.openConnection("http://127.0.0.1/api/config", connectTimeout = 1000, readTimeout = 1000)
        assertNotNull(conn)
    }
}
