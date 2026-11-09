package app.miroir.ui

import app.miroir.net.ConnectResult
import app.miroir.pairing.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorViewModelTest {
    private class FauxConnecteur(var resultat: ConnectResult) : Connector {
        var appels = 0
        var liberations = 0
        var appelsReconnexion = 0
        var resultatReconnexion: ConnectResult = ConnectResult.Ready("k")
        override suspend fun reconnect(): ConnectResult {
            appelsReconnexion++
            return resultatReconnexion
        }
        override suspend fun connect(payload: PairingPayload): ConnectResult {
            appels++
            return resultat
        }
        override fun release() { liberations++ }
    }

    private val qr = "miroir://pair?a=192.168.1.20&p=51234&t=abc&f=${"a".repeat(64)}"

    private fun vm(c: FauxConnecteur) = MirrorViewModel(c, CoroutineScope(Dispatchers.Unconfined), reconnectWindowMs = 80, retryMs = 10)

    @Test
    fun `un QR invalide reste en Scanning`() {
        val v = vm(FauxConnecteur(ConnectResult.Ready("k")))
        v.onScanRequested()
        v.onQrScanned("https://example.com")
        assertEquals(MirrorState.Scanning, v.state.value)
    }

    @Test
    fun `un QR valide mene a l'autorisation de capture puis au partage`() {
        val v = vm(FauxConnecteur(ConnectResult.Ready("k")))
        v.onScanRequested()
        v.onQrScanned(qr)
        assertEquals(MirrorState.AwaitingCapturePermission, v.state.value)
        v.onCapturePermission(true)
        assertEquals(MirrorState.Streaming, v.state.value)
    }

    @Test
    fun `le meme QR lu plusieurs fois ne declenche qu'une connexion`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        val v = vm(c)
        v.onScanRequested()
        v.onQrScanned(qr)
        v.onQrScanned(qr)
        v.onQrScanned(qr)
        assertEquals(1, c.appels)
    }

    @Test
    fun `camera refusee donne Error CameraDenied`() {
        val v = vm(FauxConnecteur(ConnectResult.Ready("k")))
        v.onScanRequested()
        v.onCameraPermission(false)
        assertEquals(MirrorState.Error(ErrorKind.CameraDenied), v.state.value)
    }

    @Test
    fun `retour a Idle apres onStop depuis une erreur`() {
        val v = vm(FauxConnecteur(ConnectResult.Ready("k")))
        v.onScanRequested()
        v.onCameraPermission(false)
        v.onStop()
        assertEquals(MirrorState.Idle, v.state.value)
    }

    @Test
    fun `capture annulee donne Error CaptureDenied et libere la connexion`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        val v = vm(c)
        v.onScanRequested()
        v.onQrScanned(qr)
        v.onCapturePermission(false)
        assertEquals(MirrorState.Error(ErrorKind.CaptureDenied), v.state.value)
        assertEquals(1, c.liberations)
    }

    @Test
    fun `Unreachable donne Error Unreachable`() {
        val v = vm(FauxConnecteur(ConnectResult.Unreachable))
        v.onScanRequested()
        v.onQrScanned(qr)
        assertEquals(MirrorState.Error(ErrorKind.Unreachable), v.state.value)
    }

    @Test
    fun `Rejected expired et invalid_token donnent Error Expired`() {
        listOf("expired", "invalid_token").forEach { raison ->
            val v = vm(FauxConnecteur(ConnectResult.Rejected(raison)))
            v.onScanRequested()
            v.onQrScanned(qr)
            assertEquals(MirrorState.Error(ErrorKind.Expired), v.state.value)
        }
    }

    @Test
    fun `Rejected busy donne Error Busy`() {
        val v = vm(FauxConnecteur(ConnectResult.Rejected("busy")))
        v.onScanRequested()
        v.onQrScanned(qr)
        assertEquals(MirrorState.Error(ErrorKind.Busy), v.state.value)
    }

    @Test
    fun `onStop pendant Streaming renvoie a Idle et libere la connexion`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        val v = vm(c)
        v.onScanRequested()
        v.onQrScanned(qr)
        v.onCapturePermission(true)
        v.onStop()
        assertEquals(MirrorState.Idle, v.state.value)
        assertEquals(1, c.liberations)
    }

    private fun enPartage(c: FauxConnecteur): MirrorViewModel {
        val v = vm(c)
        v.onScanRequested()
        v.onQrScanned(qr)
        v.onCapturePermission(true)
        return v
    }

    @Test
    fun `perte de connexion puis reprise remet Streaming sans nouvelle autorisation de capture`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        val v = enPartage(c)
        v.onConnectionLost()
        assertEquals(MirrorState.Streaming, v.state.value)
        assertEquals(1, c.appelsReconnexion)
        assertEquals(1, c.appels)
    }

    @Test
    fun `reprise impossible pendant toute la fenetre donne Error Dropped et libere`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        c.resultatReconnexion = ConnectResult.Unreachable
        val v = enPartage(c)
        v.onConnectionLost()
        Thread.sleep(200)
        assertEquals(MirrorState.Error(ErrorKind.Dropped), v.state.value)
        assertEquals(1, c.liberations)
        assertTrue(c.appelsReconnexion > 1)
    }

    @Test
    fun `reprise refusee par le Mac arrete tout de suite avec Dropped`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        c.resultatReconnexion = ConnectResult.Rejected("expired")
        val v = enPartage(c)
        v.onConnectionLost()
        assertEquals(MirrorState.Error(ErrorKind.Dropped), v.state.value)
        assertEquals(1, c.appelsReconnexion)
    }

    @Test
    fun `onConnectionLost hors Streaming est ignore`() {
        val c = FauxConnecteur(ConnectResult.Ready("k"))
        val v = vm(c)
        v.onConnectionLost()
        assertEquals(MirrorState.Idle, v.state.value)
        assertEquals(0, c.appelsReconnexion)
    }
}
