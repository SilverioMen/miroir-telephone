package app.miroir.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.miroir.R

@StringRes
fun messageErreur(kind: ErrorKind): Int = when (kind) {
    ErrorKind.CameraDenied -> R.string.erreur_camera
    ErrorKind.CaptureDenied -> R.string.erreur_capture
    ErrorKind.Unreachable -> R.string.erreur_injoignable
    ErrorKind.Expired -> R.string.erreur_expire
    ErrorKind.Busy -> R.string.erreur_occupe
    ErrorKind.Dropped -> R.string.erreur_coupure
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun HomeScreen(onStart: () -> Unit) = Page {
    Text(stringResource(R.string.titre_accueil), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.explication_accueil), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bouton_partager)) }
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.aide_batterie), style = MaterialTheme.typography.bodySmall)
}

@Composable
fun MessageScreen(@StringRes texte: Int, @StringRes bouton: Int? = null, onAction: () -> Unit = {}) = Page {
    Text(stringResource(texte), style = MaterialTheme.typography.bodyLarge)
    if (bouton != null) {
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(stringResource(bouton)) }
    }
}

@Composable
fun ScanOverlay(onCancel: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.SpaceBetween,
) {
    Text(stringResource(R.string.consigne_scan), style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.annuler)) }
}
