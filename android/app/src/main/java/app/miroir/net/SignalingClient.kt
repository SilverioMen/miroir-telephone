package app.miroir.net

import app.miroir.pairing.PairingPayload
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

sealed interface ConnectResult {
    data class Ready(val resumeKey: String) : ConnectResult
    data class Rejected(val reason: String) : ConnectResult
    data object Unreachable : ConnectResult
}

/**
 * Se connecte a l'app de bureau en WSS, en verifiant l'empreinte du certificat annoncee par le QR code.
 * Essaie chaque adresse du QR dans l'ordre.
 */
class SignalingClient(
    private val payload: PairingPayload,
    private val connectTimeoutMs: Long = 5000,
) {
    private val flux = MutableSharedFlow<SignalMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<SignalMessage> = flux.asSharedFlow()

    private val perte = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emis quand la connexion tombe sans que nous l'ayons fermee (verrouillage, Wi-Fi coupe...). */
    val dropped: SharedFlow<Unit> = perte.asSharedFlow()

    private val reprise = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emis apres une reprise reussie : l'envoi video doit renegocier. */
    val resumed: SharedFlow<Unit> = reprise.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var resumeKey: String? = null

    @Volatile
    private var ferme = false

    private val trustManager = FingerprintTrustManager(payload.fingerprint)
    private val client: OkHttpClient = run {
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // adresses IP : l'authenticite repose sur l'empreinte
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun connect(): ConnectResult =
        essaieToutes(JSONObject().put("type", "hello").put("token", payload.token).toString())

    /** Reprend la session apres une coupure, sans nouveau scan. */
    suspend fun reconnect(): ConnectResult {
        val cle = resumeKey ?: return ConnectResult.Rejected("invalid_token")
        val res = essaieToutes(JSONObject().put("type", "resume").put("resumeKey", cle).toString())
        if (res is ConnectResult.Ready) reprise.tryEmit(Unit)
        return res
    }

    private suspend fun essaieToutes(premierMessage: String): ConnectResult {
        for (adresse in payload.addresses) {
            val res = essaie(adresse, premierMessage)
            if (res !is ConnectResult.Unreachable) return res
        }
        return ConnectResult.Unreachable
    }

    private suspend fun essaie(adresse: String, premierMessage: String): ConnectResult {
        val resultat = CompletableDeferred<ConnectResult>()
        var poignee = false
        val requete = Request.Builder().url("wss://$adresse:${payload.port}/ws/phone").build()
        val ws = client.newWebSocket(requete, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(premierMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!poignee) {
                    val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (o.optString("type")) {
                        "ready" -> { poignee = true; resultat.complete(ConnectResult.Ready(o.optString("resumeKey"))) }
                        "error" -> resultat.complete(ConnectResult.Rejected(o.optString("reason")))
                    }
                } else {
                    SignalMessage.parse(text)?.let { flux.tryEmit(it) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                resultat.complete(ConnectResult.Unreachable)
                signalerPerte(webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                resultat.complete(ConnectResult.Unreachable)
                signalerPerte(webSocket)
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                resultat.complete(ConnectResult.Unreachable)
                signalerPerte(webSocket)
            }
        })
        val res = withTimeoutOrNull(connectTimeoutMs) { resultat.await() } ?: ConnectResult.Unreachable
        if (res is ConnectResult.Ready) {
            socket = ws
            resumeKey = res.resumeKey
        } else {
            ws.cancel()
        }
        return res
    }

    private fun signalerPerte(ws: WebSocket) {
        if (!ferme && socket === ws) {
            socket = null
            perte.tryEmit(Unit)
        }
    }

    fun send(msg: SignalMessage) {
        socket?.send(msg.toJson())
    }

    fun close() {
        ferme = true
        socket?.close(1000, null)
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}
