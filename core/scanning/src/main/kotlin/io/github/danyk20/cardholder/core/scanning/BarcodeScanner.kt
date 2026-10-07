package io.github.danyk20.cardholder.core.scanning

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation

/**
 * Full-screen camera scanner for loyalty card barcodes and QR codes; asks for the camera permission.
 * Meant to replace the screen content (not a dialog), so the hosting window's security flags apply.
 */
@Composable
fun BarcodeScanner(onScanned: (ScannedBarcode) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    // After "Don't allow" twice (or "Don't ask again") the system no longer shows the permission
    // dialog, so the user is sent to the app's settings instead.
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        val activity = context.findActivity()
        permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Picks up a permission granted in the system settings when the user comes back.
    LifecycleResumeEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            hasPermission = true
            permanentlyDenied = false
        }
        onPauseOrDispose {}
    }
    BackHandler(onBack = onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            CameraPreview(onScanned = onScanned)
            ViewfinderOverlay()
        } else {
            PermissionRationale(
                permanentlyDenied = permanentlyDenied,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
            )
        }
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Scrim),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Icon(CardholderIcons.Close, stringResource(R.string.scanner_close), tint = Color.White)
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (ScannedBarcode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var cameraCount by remember { mutableIntStateOf(0) }
    var cameraIndex by rememberSaveable { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer =
        remember {
            BarcodeAnalyzer { barcode -> ContextCompat.getMainExecutor(context).execute { currentOnScanned(barcode) } }
        }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(lifecycleOwner, cameraIndex) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        // Back cameras first; the front camera can still be chosen, e.g. for a card held up to it.
        val cameras = provider.availableCameraInfos
            .sortedBy { if (it.lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1 }
            .map { it.cameraSelector }
        cameraCount = cameras.size
        val selector = cameras.getOrNull(cameraIndex) ?: CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(executor, analyzer) }
        provider.unbindAll()
        camera = try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            // No such camera, or it's in use by another app.
            cameraFailed = true
            return@LaunchedEffect
        }
        try {
            awaitCancellation()
        } finally {
            camera = null
            provider.unbind(preview, analysis)
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (cameraFailed) {
            Text(
                stringResource(R.string.scanner_camera_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight()
                    .padding(32.dp),
            )
        }
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        camera?.let { bound ->
            LensControls(
                camera = bound,
                canSwitchCamera = cameraCount > 1,
                onSwitchCamera = { cameraIndex = (cameraIndex + 1) % cameraCount },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * Zoom steps and a camera switch. Phones with several back lenses usually expose them as one camera
 * whose zoom range spans them: below 1x the ultra-wide lens is used, higher steps use the telephoto.
 */
@Composable
private fun LensControls(
    camera: Camera,
    canSwitchCamera: Boolean,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = camera.cameraInfo.zoomState.value
    val steps = remember(camera) { zoom?.let { zoomSteps(it.minZoomRatio, it.maxZoomRatio) }.orEmpty() }
    var selected by remember(camera) { mutableFloatStateOf(1f) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Scrim, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (steps.size > 1) {
            steps.forEach { ratio ->
                val label = formatZoom(ratio)
                val description = stringResource(R.string.scanner_zoom, label)
                TextButton(
                    onClick = {
                        selected = ratio
                        camera.cameraControl.setZoomRatio(ratio)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (ratio == selected) Color(0xFFFFD54F) else Color.White,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = description
                        this.selected = ratio == selected
                    },
                ) {
                    Text(label, fontWeight = if (ratio == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        if (canSwitchCamera) {
            IconButton(onClick = onSwitchCamera) {
                Icon(CardholderIcons.SwitchCamera, stringResource(R.string.scanner_switch_camera), tint = Color.White)
            }
        }
    }
}

/** Zoom ratios matching common telephoto lenses. */
private val TelephotoSteps = listOf(2f, 3f, 5f)

/** The ultra-wide end (rounded to one decimal), 1x and the telephoto steps the camera supports. */
private fun zoomSteps(min: Float, max: Float): List<Float> = buildList {
    if (min < 1f) add((min * TENTHS).roundToInt() / TENTHS)
    add(1f)
    TelephotoSteps.filter { it <= max }.forEach(::add)
}

private const val TENTHS = 10f

private fun formatZoom(ratio: Float): String =
    if (ratio % 1f == 0f) "${ratio.toInt()}×" else String.format(Locale.getDefault(), "%.1f×", ratio)

@Composable
private fun ViewfinderOverlay() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(VIEWFINDER_ASPECT_RATIO)
                .border(3.dp, Color.White, RoundedCornerShape(16.dp)),
        )
        Text(
            stringResource(R.string.scanner_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .background(Scrim, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PermissionRationale(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(CardholderIcons.Camera, contentDescription = null, tint = Color.White)
        Text(
            stringResource(
                if (permanentlyDenied) R.string.scanner_permission_denied else R.string.scanner_permission_rationale,
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.scanner_open_settings)) }
        } else {
            Button(onClick = onRequest) { Text(stringResource(R.string.scanner_grant_permission)) }
        }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some customised systems lack this screen; the user can still find it in Settings > Apps.
    }
}

private const val VIEWFINDER_ASPECT_RATIO = 1.4f

/** Keeps white overlay content readable on top of a bright camera image. */
private val Scrim = Color.Black.copy(alpha = 0.55f)
