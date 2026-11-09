package app.miroir.ui

import androidx.lifecycle.ViewModel
import app.miroir.net.ConnectResult
import app.miroir.pairing.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MirrorViewModel(
    private val connector: Connector,
    private val scope: CoroutineScope,
    private val reconnectWindowMs: Long = 120_000,
    private val retryMs: Long = 2_000,
) : ViewModel() {
    constructor() : this(DefaultConnector(), CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate))

    private val etat = MutableStateFlow<MirrorState>(MirrorState.Idle)
    val state: StateFlow<MirrorState> = etat

    fun onScanRequested() {
        if (etat.value is MirrorState.Idle || etat.value is MirrorState.Error) etat.value = MirrorState.Scanning
    }

    fun onCameraPermission(granted: Boolean) {
        if (!granted && etat.value == MirrorState.Scanning) etat.value = MirrorState.Error(ErrorKind.CameraDenied)
    }

    fun onQrScanned(text: String) {
        if (etat.value != MirrorState.Scanning) return
        val payload = PairingPayload.parse(text) ?: return
        etat.value = MirrorState.Connecting
        scope.launch {
            etat.value = when (val res = connector.connect(payload)) {
                is ConnectResult.Ready -> MirrorState.AwaitingCapturePermission
                is ConnectResult.Rejected -> MirrorState.Error(
                    if (res.reason == "busy") ErrorKind.Busy else ErrorKind.Expired,
                )
                ConnectResult.Unreachable -> MirrorState.Error(ErrorKind.Unreachable)
            }
        }
    }

    fun onCapturePermission(granted: Boolean) {
        if (etat.value != MirrorState.AwaitingCapturePermission) return
        if (granted) {
            etat.value = MirrorState.Streaming
        } else {
            connector.release()
            etat.value = MirrorState.Error(ErrorKind.CaptureDenied)
        }
    }

    /** Coupure pendant le partage : on retente la reprise pendant 2 minutes, sans nouveau scan. */
    fun onConnectionLost() {
        if (etat.value != MirrorState.Streaming) return
        etat.value = MirrorState.Connecting
        scope.launch {
            val debut = System.currentTimeMillis()
            while (true) {
                when (connector.reconnect()) {
                    is ConnectResult.Ready -> { etat.value = MirrorState.Streaming; return@launch }
                    is ConnectResult.Rejected -> break
                    ConnectResult.Unreachable -> Unit
                }
                if (System.currentTimeMillis() - debut >= reconnectWindowMs) break
                delay(retryMs)
            }
            connector.release()
            etat.value = MirrorState.Error(ErrorKind.Dropped)
        }
    }

    fun onStop() {
        connector.release()
        etat.value = MirrorState.Idle
    }

    override fun onCleared() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onCleared()
    }
}
