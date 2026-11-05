package org.openhue.streamtv.hue.stream

import org.bouncycastle.tls.BasicTlsPSKIdentity
import org.bouncycastle.tls.CipherSuite
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsPSKIdentity
import org.bouncycastle.tls.crypto.TlsCrypto
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.security.SecureRandom

class HueDtlsClient(
    private val username: String,
    private val clientKeyHex: String,
    crypto: TlsCrypto = BcTlsCrypto(SecureRandom())
) : DefaultTlsClient(crypto) {

    private val pskIdentityBytes = username.toByteArray(Charsets.UTF_8)
    private val pskSecretBytes = HuePacketBuilder.hexToBytes(clientKeyHex)

    override fun getSupportedCipherSuites(): IntArray {
        return intArrayOf(
            CipherSuite.TLS_PSK_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_PSK_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_PSK_WITH_AES_128_CBC_SHA256
        )
    }

    override fun getProtocolVersions(): Array<ProtocolVersion> {
        return ProtocolVersion.DTLSv12.only()
    }

    override fun getPSKIdentity(): TlsPSKIdentity {
        return BasicTlsPSKIdentity(pskIdentityBytes, pskSecretBytes)
    }

    override fun getAuthentication(): TlsAuthentication? {
        return null
    }
}
