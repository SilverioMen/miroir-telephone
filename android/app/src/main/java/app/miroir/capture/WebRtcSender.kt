package app.miroir.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import app.miroir.net.SignalMessage
import app.miroir.net.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** Capture l'ecran et l'envoie en WebRTC a l'app de bureau. Reglages initiaux : 1280 px, 30 i/s, 8 Mbit/s. */
class WebRtcSender(
    private val context: Context,
    private val signaling: SignalingClient,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val GRAND_COTE = 1280
        private const val FPS = 30
        private const val DEBIT_MAX_BPS = 8_000_000
        private var initialise = false
    }

    private var egl: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var helper: SurfaceTextureHelper? = null
    private var source: VideoSource? = null
    private var track: VideoTrack? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var pc: PeerConnection? = null
    private var ecoute: Job? = null

    fun start(projectionData: Intent, onStopped: () -> Unit) {
        if (!initialise) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            initialise = true
        }
        val eglBase = EglBase.create().also { egl = it }
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        val f = factory!!

        val h = SurfaceTextureHelper.create("capture", eglBase.eglBaseContext).also { helper = it }
        val s = f.createVideoSource(true).also { source = it }
        val cap = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {
            override fun onStop() = onStopped()
        }).also { capturer = it }
        cap.initialize(h, context, s.capturerObserver)
        val (w, hh) = tailleCapture()
        cap.startCapture(w, hh, FPS)

        track = f.createVideoTrack("ecran", s)
        ouvrirConnexion()

        ecoute = scope.launch {
            launch {
                signaling.incoming.collect { msg ->
                    when (msg) {
                        is SignalMessage.Answer -> pc?.setRemoteDescription(SdpVide, SessionDescription(SessionDescription.Type.ANSWER, msg.sdp))
                        is SignalMessage.Ice -> pc?.addIceCandidate(IceCandidate(msg.sdpMid, msg.sdpMLineIndex, msg.candidate))
                        SignalMessage.Bye -> onStopped()
                        is SignalMessage.Offer -> Unit
                    }
                }
            }
            // Apres une reprise, le Mac a ferme sa connexion : on en recree une avec la meme capture.
            launch { signaling.resumed.collect { pc?.close(); pc?.dispose(); ouvrirConnexion() } }
        }
    }

    private fun ouvrirConnexion() {
        val f = factory!!
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val connexion = f.createPeerConnection(config, observateur())!!.also { pc = it }
        val sender = connexion.addTrack(track!!, listOf("miroir"))
        val params = sender.parameters
        params.encodings.forEach {
            it.maxBitrateBps = DEBIT_MAX_BPS
            it.maxFramerate = FPS
        }
        sender.parameters = params
        connexion.createOffer(object : SdpVide() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                connexion.setLocalDescription(SdpVide, sdp)
                signaling.send(SignalMessage.Offer(sdp.description))
            }
        }, MediaConstraints())
    }

    fun stop() {
        ecoute?.cancel()
        runCatching { signaling.send(SignalMessage.Bye) }
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        track?.dispose()
        source?.dispose()
        pc?.close()
        pc?.dispose()
        helper?.dispose()
        factory?.dispose()
        egl?.release()
        capturer = null; track = null; source = null; pc = null; helper = null; factory = null; egl = null
    }

    private fun tailleCapture(): Pair<Int, Int> {
        val m = context.resources.displayMetrics
        val grand = maxOf(m.widthPixels, m.heightPixels).toFloat()
        val echelle = minOf(1f, GRAND_COTE / grand)
        fun pair(v: Int) = (v * echelle).toInt() and 1.inv()
        return pair(m.widthPixels) to pair(m.heightPixels)
    }

    private fun observateur() = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            signaling.send(SignalMessage.Ice(c.sdp, c.sdpMid, c.sdpMLineIndex))
        }
        override fun onSignalingChange(s: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(b: Boolean) = Unit
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) = Unit
        override fun onAddStream(s: MediaStream) = Unit
        override fun onRemoveStream(s: MediaStream) = Unit
        override fun onDataChannel(d: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) = Unit
    }

    private open class SdpVide : SdpObserver {
        companion object : SdpVide()
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
