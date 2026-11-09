package app.miroir.ui

import app.miroir.net.ConnectResult
import app.miroir.net.SignalMessage
import app.miroir.net.SignalingClient
import app.miroir.pairing.PairingPayload

/** Connexion partagee entre l'interface et le service : le jeton du QR n'est utilisable qu'une fois. */
object SessionHolder {
    @Volatile
    var client: SignalingClient? = null

    fun release() {
        client?.let {
            runCatching { it.send(SignalMessage.Bye) }
            it.close()
        }
        client = null
    }
}

interface Connector {
    suspend fun connect(payload: PairingPayload): ConnectResult
    fun release()
}

class DefaultConnector : Connector {
    override suspend fun connect(payload: PairingPayload): ConnectResult {
        val client = SignalingClient(payload)
        val res = client.connect()
        if (res is ConnectResult.Ready) SessionHolder.client = client else client.close()
        return res
    }

    override fun release() = SessionHolder.release()
}
