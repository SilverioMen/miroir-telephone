package app.miroir.ui

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@Composable
fun QrScannerScreen(onQr: (String) -> Unit) {
    val activite = LocalContext.current as ComponentActivity
    val fournisseur = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val lecteur = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(Unit) {
        onDispose {
            fournisseur[0]?.unbindAll()
            lecteur.close()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val apercu = PreviewView(ctx)
            val futur = ProcessCameraProvider.getInstance(ctx)
            futur.addListener({
                val provider = futur.get().also { fournisseur[0] = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = apercu.surfaceProvider }
                val analyse = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analyse.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                    val media = image.image
                    if (media == null) {
                        image.close()
                    } else {
                        lecteur.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onQr) }
                            .addOnCompleteListener { image.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(activite, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyse)
            }, ContextCompat.getMainExecutor(ctx))
            apercu
        },
    )
}
