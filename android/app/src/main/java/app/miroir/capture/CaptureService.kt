package app.miroir.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.miroir.R
import app.miroir.net.ConnectResult
import app.miroir.net.SignalingClient
import app.miroir.pairing.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Service de premier plan (type mediaProjection) qui porte le partage d'ecran. */
class CaptureService : Service() {
    companion object {
        const val EXTRA_PROJECTION = "projection"
        const val EXTRA_PAIRING = "pairing"
        const val ACTION_STOP = "app.miroir.STOP"
        private const val CANAL = "partage"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var signaling: SignalingClient? = null
    private var sender: WebRtcSender? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            arreter()
            return START_NOT_STICKY
        }
        val projection = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION)
        val payload = intent?.getStringExtra(EXTRA_PAIRING)?.let { PairingPayload.parse(it) }
        if (projection == null || payload == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14 : le service doit etre au premier plan avant de demarrer la capture.
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        CaptureBus.publish(CaptureStatus.Connecting)

        val client = SignalingClient(payload).also { signaling = it }
        scope.launch {
            when (val res = client.connect()) {
                is ConnectResult.Ready -> {
                    val s = WebRtcSender(applicationContext, client, scope).also { sender = it }
                    s.start(projection, onStopped = { arreter() })
                    CaptureBus.publish(CaptureStatus.Streaming)
                }
                else -> {
                    CaptureBus.publish(CaptureStatus.Failed(res))
                    arreter(garderEtat = true)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun arreter(garderEtat: Boolean = false) {
        sender?.stop()
        sender = null
        signaling?.close()
        signaling = null
        if (!garderEtat) CaptureBus.publish(CaptureStatus.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val gestionnaire = getSystemService(NotificationManager::class.java)
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL, getString(R.string.canal_partage), NotificationManager.IMPORTANCE_LOW),
        )
        val arret = PendingIntent.getService(
            this, 0,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CANAL)
            .setContentTitle(getString(R.string.partage_en_cours))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .addAction(Notification.Action.Builder(null, getString(R.string.arreter), arret).build())
            .setOngoing(true)
            .build()
    }
}
