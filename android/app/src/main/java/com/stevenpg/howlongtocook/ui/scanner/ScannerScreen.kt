package com.stevenpg.howlongtocook.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.ui.components.EmptyState

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onReturnBarcode: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onCreateProduct: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    var manualEntry by rememberSaveable { mutableStateOf(false) }
    val analyzer = remember {
        BarcodeAnalyzer { code -> viewModel.onBarcode(code) }
    }
    // One analysis thread per scanner visit, released when the screen leaves composition.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember { createController(context, analyzer, analysisExecutor) }
    DisposableEffect(Unit) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(outcome) {
        when (val o = outcome) {
            null -> Unit
            is ScanOutcome.ReturnToEditor -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.consumed(); onReturnBarcode(o.code)
            }
            is ScanOutcome.OpenProduct -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.consumed(); onOpenProduct(o.id)
            }
            is ScanOutcome.CreateProduct -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.consumed(); onCreateProduct(o.code)
            }
            is ScanOutcome.Choose -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(controller)
            ScanOverlay()
        } else {
            Surface(Modifier.fillMaxSize()) {
                EmptyState(
                    icon = Icons.Outlined.NoPhotography,
                    title = stringResource(R.string.scanner_permission_title),
                    message = stringResource(R.string.scanner_permission_message),
                    modifier = Modifier.align(Alignment.Center).padding(top = 96.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = {
                            if (askedOnce) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) { Text(stringResource(if (askedOnce) R.string.scanner_open_settings else R.string.scanner_allow_camera)) }
                        TextButton(onClick = { manualEntry = true }) { Text(stringResource(R.string.scanner_enter_manually_long)) }
                    }
                }
            }
        }

        // Top controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            if (hasPermission) TorchButton(controller)
        }

        // Bottom panel
        if (hasPermission) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (viewModel.addToEditor) R.string.scanner_prompt_add else R.string.scanner_prompt_lookup),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.scanner_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { manualEntry = true }) {
                        Icon(Icons.Outlined.Keyboard, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.scanner_enter_manually))
                    }
                }
            }
        }
    }

    if (manualEntry) {
        ManualEntryDialog(
            onDismiss = { manualEntry = false },
            onSubmit = { manualEntry = false; viewModel.onBarcode(it) },
        )
    }

    (outcome as? ScanOutcome.Choose)?.let { choose ->
        AlertDialog(
            onDismissRequest = { viewModel.consumed(); analyzer.resume() },
            title = { Text(stringResource(R.string.scanner_multiple_title, choose.code)) },
            text = {
                Column {
                    choose.products.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = p.brand?.let { b -> { Text(b) } },
                            modifier = Modifier.clickable { viewModel.consumed(); onOpenProduct(p.id) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumed(); onCreateProduct(choose.code) }) { Text(stringResource(R.string.scanner_add_new)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.consumed(); analyzer.resume() }) { Text(stringResource(R.string.scanner_scan_again)) }
            },
        )
    }
}

private fun createController(context: android.content.Context, analyzer: BarcodeAnalyzer, executor: Executor) =
    LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        imageAnalysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        setImageAnalysisAnalyzer(executor, analyzer)
    }

@Composable
private fun CameraPreview(controller: LifecycleCameraController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TorchButton(controller: LifecycleCameraController) {
    var on by remember { mutableStateOf(false) }
    FilledTonalIconButton(
        onClick = {
            on = !on
            controller.enableTorch(on)
        },
        colors = if (on) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
    ) {
        Icon(
            if (on) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
            contentDescription = stringResource(if (on) R.string.scanner_torch_off else R.string.scanner_torch_on),
        )
    }
}

/** Dims everything except a rounded scanning window. */
@Composable
private fun ScanOverlay() {
    val frameColor = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val w = size.width * 0.8f
        val h = w * 0.6f
        val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2.6f)
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, GeoSize(w, h), radius, blendMode = BlendMode.Clear)
        drawRoundRect(frameColor, topLeft, GeoSize(w, h), radius, style = Stroke(width = 4.dp.toPx()))
    }
}

@Composable
private fun ManualEntryDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val valid = text.trim().length in 4..64
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Keyboard, null) },
        title = { Text(stringResource(R.string.scanner_manual_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isLetterOrDigit() || it == '-' }.take(64) },
                label = { Text(stringResource(R.string.scanner_manual_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSubmit(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = valid) { Text(stringResource(R.string.scanner_look_up)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
