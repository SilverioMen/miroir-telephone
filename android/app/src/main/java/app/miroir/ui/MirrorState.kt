package app.miroir.ui

enum class ErrorKind { CameraDenied, CaptureDenied, Unreachable, Expired, Busy, Dropped }

sealed interface MirrorState {
    data object Idle : MirrorState
    data object Scanning : MirrorState
    data object Connecting : MirrorState
    data object AwaitingCapturePermission : MirrorState
    data object Streaming : MirrorState
    data class Error(val kind: ErrorKind) : MirrorState
}
