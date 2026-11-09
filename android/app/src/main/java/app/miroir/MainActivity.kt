package app.miroir

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.miroir.capture.CaptureBus
import app.miroir.capture.CaptureService
import app.miroir.capture.CaptureStatus
import app.miroir.ui.HomeScreen
import app.miroir.ui.MessageScreen
import app.miroir.ui.MirrorState
import app.miroir.ui.MirrorViewModel
import app.miroir.ui.QrScannerScreen
import app.miroir.ui.ScanOverlay
import app.miroir.ui.SessionHolder
import app.miroir.ui.messageErreur

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urlDeTest = if (BuildConfig.DEBUG) intent.getStringExtra("url") else null
        setContent { MaterialTheme { Racine(urlDeTest) } }
    }

    @Composable
    private fun Racine(urlDeTest: String?) {
        val vm: MirrorViewModel = viewModel()
        val etat by vm.state.collectAsState()
        val bus by CaptureBus.state.collectAsState()
        val gestionnaire = getSystemService(MediaProjectionManager::class.java)

        val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == RESULT_OK && data != null) {
                vm.onCapturePermission(true)
                startForegroundService(Intent(this, CaptureService::class.java).putExtra(CaptureService.EXTRA_PROJECTION, data))
            } else {
                vm.onCapturePermission(false)
            }
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            capture.launch(gestionnaire.createScreenCaptureIntent())
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onCameraPermission(it) }

        LaunchedEffect(urlDeTest) {
            if (urlDeTest != null) { vm.onScanRequested(); vm.onQrScanned(urlDeTest) }
        }
        LaunchedEffect(Unit) { SessionHolder.lost.collect { vm.onConnectionLost() } }
        LaunchedEffect(etat) {
            if (etat == MirrorState.AwaitingCapturePermission) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Le service s'est arrete (bouton Arreter de la notification) : on revient a l'accueil.
        var avaitPartage by remember { mutableStateOf(false) }
        LaunchedEffect(bus) {
            if (bus == CaptureStatus.Streaming) avaitPartage = true
            else if (bus == CaptureStatus.Idle && avaitPartage) { avaitPartage = false; vm.onStop() }
        }

        when (val s = etat) {
            MirrorState.Idle -> HomeScreen(onStart = { vm.onScanRequested(); camera.launch(Manifest.permission.CAMERA) })
            MirrorState.Scanning -> { QrScannerScreen(onQr = vm::onQrScanned); ScanOverlay(onCancel = vm::onStop) }
            MirrorState.Connecting -> MessageScreen(R.string.connexion_en_cours)
            MirrorState.AwaitingCapturePermission -> MessageScreen(R.string.attente_autorisation)
            MirrorState.Streaming -> MessageScreen(R.string.partage_actif, R.string.arreter) {
                startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                vm.onStop()
            }
            is MirrorState.Error -> MessageScreen(messageErreur(s.kind), R.string.reessayer) {
                vm.onStop(); vm.onScanRequested(); camera.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
