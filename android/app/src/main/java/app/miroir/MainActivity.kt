package app.miroir

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.miroir.capture.CaptureBus
import app.miroir.capture.CaptureService
import app.miroir.pairing.PairingPayload

/** Ecran de prototype (tache 5) : coller le texte du QR puis demarrer. L'interface finale vient en tache 7. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urlInitiale = intent.getStringExtra("url").orEmpty()
        val auto = intent.getBooleanExtra("autostart", false)
        setContent { Prototype(urlInitiale, auto) }
    }

    @Composable
    private fun Prototype(urlInitiale: String, auto: Boolean) {
        var url by remember { mutableStateOf(urlInitiale) }
        val statut by CaptureBus.state.collectAsState()
        val gestionnaire = getSystemService(MediaProjectionManager::class.java)

        val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data ?: return@rememberLauncherForActivityResult
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_PROJECTION, data)
                    .putExtra(CaptureService.EXTRA_PAIRING, url),
            )
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            capture.launch(gestionnaire.createScreenCaptureIntent())
        }
        val demarrer = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }

        LaunchedEffect(Unit) { if (auto && PairingPayload.parse(url) != null) demarrer() }

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Texte du QR") })
            Button(onClick = demarrer, enabled = PairingPayload.parse(url) != null) { Text("Démarrer") }
            Text("État : $statut")
        }
    }
}
