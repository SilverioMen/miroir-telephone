package app.miroir.ui

import androidx.lifecycle.ViewModel
import app.miroir.net.ConnectResult
import app.miroir.pairing.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MirrorViewModel(
    private val connector: Connector,
    private val scope: CoroutineScope,
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

    fun onStop() {
        connector.release()
        etat.value = MirrorState.Idle
    }

    override fun onCleared() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onCleared()
    }
}
