package app.miroir.net

import app.miroir.pairing.PairingPayload
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingClientTest {
    private val serveurs = mutableListOf<MockWebServer>()
    private val clients = mutableListOf<SignalingClient>()

    @After
    fun nettoie() {
        clients.forEach { it.close() }
        serveurs.forEach { runCatching { it.shutdown() } }
    }

    private class Serveur(val server: MockWebServer, val empreinte: String)

    private fun empreinteDe(hc: HeldCertificate): String =
        MessageDigest.getInstance("SHA-256").digest(hc.certificate.encoded)
            .joinToString("") { "%02x".format(it) }

    private fun demarre(reponse: (String) -> String?): Serveur {
        val hc = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(hc).build()
        val server = MockWebServer()
        server.useHttps(tls.sslSocketFactory(), false)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                reponse(text)?.let { webSocket.send(it) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
        }))
        server.start()
        serveurs += server
        return Serveur(server, empreinteDe(hc))
    }

    private fun client(s: Serveur, adresses: List<String> = listOf("127.0.0.1"), empreinte: String = s.empreinte): SignalingClient {
        val payload = PairingPayload(adresses, s.server.port, "tok", empreinte)
        return SignalingClient(payload, connectTimeoutMs = 700).also { clients += it }
    }

    @Test
    fun `connect renvoie Ready apres un hello accepte`() = runBlocking {
        val recu = AtomicReference<String>()
        val s = demarre { texte -> recu.set(texte); """{"type":"ready","resumeKey":"k1"}""" }
        val res = client(s).connect()
        assertEquals(ConnectResult.Ready("k1"), res)
        assertTrue(recu.get().contains(""""type":"hello"""") && recu.get().contains(""""token":"tok""""))
    }

    @Test
    fun `connect renvoie Rejected expired quand le serveur repond expired`() = runBlocking {
        val s = demarre { """{"type":"error","reason":"expired"}""" }
        assertEquals(ConnectResult.Rejected("expired"), client(s).connect())
    }

    @Test
    fun `connect essaie l'adresse suivante quand la premiere ne repond pas`() = runBlocking {
        val s = demarre { """{"type":"ready","resumeKey":"k2"}""" }
        val res = client(s, adresses = listOf("10.255.255.1", "127.0.0.1")).connect()
        assertEquals(ConnectResult.Ready("k2"), res)
    }

    @Test
    fun `connect renvoie Unreachable quand aucune adresse ne repond`() = runBlocking {
        val s = demarre { null }
        s.server.shutdown()
        assertEquals(ConnectResult.Unreachable, client(s).connect())
    }

    @Test
    fun `connect renvoie Unreachable si l'empreinte du certificat ne correspond pas`() = runBlocking {
        val s = demarre { """{"type":"ready","resumeKey":"k3"}""" }
        assertEquals(ConnectResult.Unreachable, client(s, empreinte = "0".repeat(64)).connect())
    }

    @Test
    fun `incoming transmet les messages answer et ice du serveur`() = runBlocking {
        val s = demarre { texte ->
            when {
                texte.contains("hello") -> """{"type":"ready","resumeKey":"k4"}"""
                texte.contains("offer") -> """{"type":"answer","sdp":"reponse"}"""
                else -> null
            }
        }
        val c = client(s)
        assertEquals(ConnectResult.Ready("k4"), c.connect())
        val recu = coroutineScope {
            val attente = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3000) { c.incoming.first() } }
            c.send(SignalMessage.Offer("o"))
            attente.await()
        }
        assertEquals(SignalMessage.Answer("reponse"), recu)
    }

    @Test
    fun `SignalMessage fait l'aller-retour JSON pour ice et bye`() {
        val ice = SignalMessage.Ice("candidate:1", "0", 0)
        assertEquals(ice, SignalMessage.parse(ice.toJson()))
        assertEquals(SignalMessage.Bye, SignalMessage.parse(SignalMessage.Bye.toJson()))
        assertEquals(null, SignalMessage.parse("pas du json"))
    }
}
