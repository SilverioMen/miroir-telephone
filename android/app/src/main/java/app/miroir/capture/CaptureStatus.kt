package app.miroir.capture

import app.miroir.net.ConnectResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface CaptureStatus {
    data object Idle : CaptureStatus
    data object Connecting : CaptureStatus
    data object Streaming : CaptureStatus
    data class Failed(val result: ConnectResult) : CaptureStatus
}

/** Etat du partage, publie par le service et observe par l'interface. */
object CaptureBus {
    private val etat = MutableStateFlow<CaptureStatus>(CaptureStatus.Idle)
    val state: StateFlow<CaptureStatus> = etat
    fun publish(s: CaptureStatus) { etat.value = s }
}
