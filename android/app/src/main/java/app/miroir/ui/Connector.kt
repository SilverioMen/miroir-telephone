package app.miroir.ui

import app.miroir.net.ConnectResult
import app.miroir.net.SignalMessage
import app.miroir.net.SignalingClient
import app.miroir.pairing.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Connexion partagee entre l'interface et le service : le jeton du QR n'est utilisable qu'une fois. */
object SessionHolder {
    @Volatile
    var client: SignalingClient? = null

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var surveillance: Job? = null
    private val perte = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emis quand la connexion tombe : l'interface lance alors la reprise. */
    val lost: SharedFlow<Unit> = perte.asSharedFlow()

    fun observe(c: SignalingClient) {
        surveillance?.cancel()
        surveillance = portee.launch { c.dropped.collect { perte.emit(Unit) } }
    }

    fun release() {
        surveillance?.cancel()
        surveillance = null
        client?.let {
            runCatching { it.send(SignalMessage.Bye) }
            it.close()
        }
        client = null
    }
}

interface Connector {
    suspend fun connect(payload: PairingPayload): ConnectResult
    suspend fun reconnect(): ConnectResult
    fun release()
}

class DefaultConnector : Connector {
    override suspend fun connect(payload: PairingPayload): ConnectResult {
        val client = SignalingClient(payload)
        val res = client.connect()
        if (res is ConnectResult.Ready) {
            SessionHolder.client = client
            SessionHolder.observe(client)
        } else {
            client.close()
        }
        return res
    }

    override suspend fun reconnect(): ConnectResult =
        SessionHolder.client?.reconnect() ?: ConnectResult.Rejected("invalid_token")

    override fun release() = SessionHolder.release()
}
