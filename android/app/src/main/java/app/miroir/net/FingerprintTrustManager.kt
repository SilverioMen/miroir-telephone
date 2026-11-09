package app.miroir.net

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Accepte uniquement le certificat serveur dont l'empreinte SHA-256 est celle du QR code. */
class FingerprintTrustManager(expectedHex: String) : X509TrustManager {
    private val attendu = expectedHex.lowercase().toByteArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val feuille = chain?.firstOrNull() ?: throw CertificateException("Aucun certificat presente")
        val empreinte = MessageDigest.getInstance("SHA-256").digest(feuille.encoded)
            .joinToString("") { "%02x".format(it) }.toByteArray()
        if (!MessageDigest.isEqual(attendu, empreinte)) {
            throw CertificateException("Empreinte du certificat differente de celle du QR code")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Authentification client non prise en charge")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
