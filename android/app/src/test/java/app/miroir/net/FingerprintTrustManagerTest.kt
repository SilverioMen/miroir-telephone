package app.miroir.net

import java.io.File
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertThrows
import org.junit.Test

class FingerprintTrustManagerTest {
    private fun charge(nom: String): X509Certificate {
        val flux = javaClass.classLoader!!.getResourceAsStream(nom)!!
        return CertificateFactory.getInstance("X.509").generateCertificate(flux) as X509Certificate
    }

    private val empreinteA = javaClass.classLoader!!.getResource("cert-a.sha256")!!.readText().trim()

    @Test
    fun `accepte le certificat d'empreinte attendue`() {
        FingerprintTrustManager(empreinteA).checkServerTrusted(arrayOf(charge("cert-a.pem")), "RSA")
    }

    @Test
    fun `refuse un autre certificat`() {
        assertThrows(CertificateException::class.java) {
            FingerprintTrustManager(empreinteA).checkServerTrusted(arrayOf(charge("cert-b.pem")), "RSA")
        }
    }

    @Test
    fun `refuse une chaine vide`() {
        assertThrows(CertificateException::class.java) {
            FingerprintTrustManager(empreinteA).checkServerTrusted(emptyArray(), "RSA")
        }
    }
}
