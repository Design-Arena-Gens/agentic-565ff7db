package com.example.rccar

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rccar.ui.theme.RCCarControllerTheme
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: RcCarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RCCarControllerTheme(darkTheme = true) {
                val uiState by viewModel.uiState.collectAsState()
                val permissionHelper = rememberPermissionHelper { results ->
                    viewModel.onPermissionsResult(results.values.all { it })
                }
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            if (PermissionHelper.hasPermissions(this@MainActivity)) {
                                viewModel.onPermissionsResult(true)
                            } else {
                                permissionHelper.launchPermissions()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                MainScreen(
                    uiState = uiState,
                    onCommand = { viewModel.sendCommand(it) },
                    onConnect = { viewModel.findAndConnect() },
                    onDismissSnackbar = { viewModel.dismissSnackbar() }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    uiState: RcCarUiState,
    onCommand: (Char) -> Unit,
    onConnect: () -> Unit,
    onDismissSnackbar: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugPanel by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.showRetrySnackbar) {
        if (uiState.showRetrySnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = "Connection failed. Can't send command.",
                actionLabel = "Retry",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                onConnect()
            }
            onDismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            StopFab(
                isConnected = uiState.connectionState == ConnectionState.Connected,
                onStop = { onCommand('S') }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y > 20) { // Swipe down
                            showDebugPanel = true
                        } else if (dragAmount.y < -20) { // Swipe up
                            showDebugPanel = false
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                StatusBar(uiState.connectionState, uiState.lastCommand)
                Joystick(
                    enabled = uiState.connectionState == ConnectionState.Connected,
                    onCommand = onCommand
                )
                Spacer(modifier = Modifier.height(100.dp))
            }

            AnimatedVisibility(
                visible = !uiState.hasPermissions || (uiState.connectionState == ConnectionState.Disconnected && uiState.bondedDevices.none { "HC-05" in it }),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OnboardingOverlay(uiState, onConnect)
            }

            AnimatedVisibility(
                visible = showDebugPanel,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                DebugPanel(uiState) { showDebugPanel = false }
            }
        }
    }
}

@Composable
fun StatusBar(connectionState: ConnectionState, lastCommand: Char?) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.Connected -> Color.Green
            ConnectionState.Connecting -> Color.Yellow
            ConnectionState.Disconnected -> Color.Red
        }, label = "statusColor"
    )
    val statusText = when (connectionState) {
        ConnectionState.Connected -> "Connected to HC-05"
        ConnectionState.Connecting -> "Connecting..."
        ConnectionState.Disconnected -> "Disconnected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            drawCircle(color = statusColor)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = statusText, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        if (lastCommand != null) {
            val (icon, desc) = when (lastCommand) {
                'F' -> Icons.Default.ArrowUpward to "Forward"
                'B' -> Icons.Default.ArrowDownward to "Backward"
                'L' -> Icons.Default.ArrowBack to "Left"
                'R' -> Icons.Default.ArrowForward to "Right"
                'S' -> Icons.Default.Stop to "Stop"
                else -> null to "Unknown"
            }
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = desc, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "→ $desc", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Joystick(enabled: Boolean, onCommand: (Char) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHapticFeedback(context)
    var lastCommand by remember { mutableStateOf('S') }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val alpha = if (enabled) 1f else 0.5f

    Box(
        modifier = Modifier
            .size(300.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        if (lastCommand != 'S') {
                            onCommand('S')
                            lastCommand = 'S'
                        }
                    },
                    onDrag = { change, _ ->
                        val size = this.size
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = atan2(change.position.y - center.y, change.position.x - center.x) * (180 / Math.PI)
                        val newCommand = when {
                            angle > -45 && angle <= 45 -> 'R'
                            angle > 45 && angle <= 135 -> 'B'
                            angle > 135 || angle <= -135 -> 'L'
                            else -> 'F'
                        }
                        if (newCommand != lastCommand) {
                            onCommand(newCommand)
                            haptics.performHapticFeedback()
                            lastCommand = newCommand
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
            // Draw crosshairs
            drawLine(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height)
            )
            drawLine(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y)
            )
        }

        val buttonSize = 80.dp
        val directions = listOf(
            'F' to (Alignment.TopCenter to Icons.Default.ArrowUpward),
            'L' to (Alignment.CenterStart to Icons.Default.ArrowBack),
            'R' to (Alignment.CenterEnd to Icons.Default.ArrowForward),
            'B' to (Alignment.BottomCenter to Icons.Default.ArrowDownward)
        )

        directions.forEach { (cmd, pair) ->
            val (alignment, icon) = pair
            Box(
                modifier = Modifier
                    .align(alignment)
                    .size(buttonSize)
            ) {
                val btnInteractionSource = remember { MutableInteractionSource() }
                val isBtnPressed by btnInteractionSource.collectIsPressedAsState()
                if (isBtnPressed && enabled) {
                    LaunchedEffect(Unit) {
                        onCommand(cmd)
                        haptics.performHapticFeedback()
                    }
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Direction $cmd",
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            enabled = enabled,
                            interactionSource = btnInteractionSource,
                            indication = androidx.compose.material.ripple.rememberRipple(bounded = false, radius = 40.dp),
                            onClick = {},
                            onLongClick = { /* Show tooltip if needed */ }
                        )
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StopFab(isConnected: Boolean, onStop: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    FloatingActionButton(
        onClick = { /* Handled by combinedClickable */ },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        shape = CircleShape,
        modifier = Modifier
            .size(80.dp)
            .combinedClickable(
                onClick = { if (isConnected) onStop() },
                onDoubleClick = {
                    if (isConnected) {
                        onStop()
                        onStop()
                        onStop()
                    }
                }
            )
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun OnboardingOverlay(uiState: RcCarUiState, onConnect: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connect to RC Car",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val text = if (!uiState.hasPermissions) {
                    "This app needs Bluetooth permissions to control your car. Please grant them to continue."
                } else {
                    "Your HC-05 Bluetooth module was not found. Please make sure it's paired in your phone's Bluetooth settings and turned on."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onConnect) {
                    if (uiState.connectionState == ConnectionState.Connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Scan & Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun DebugPanel(uiState: RcCarUiState, onClose: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Debug Panel", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            DebugInfoRow(Icons.Default.Info, "Socket State", uiState.connectionState.name)
            DebugInfoRow(Icons.Default.Bluetooth, "Permissions Granted", uiState.hasPermissions.toString())
            Spacer(modifier = Modifier.height(16.dp))
            Text("Command History (Last 10)", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.height(150.dp)) {
                items(uiState.commandHistory) { cmd ->
                    Row {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(cmd)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugInfoRow(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$title:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(4.dp))
        Text(value)
    }
}

@Composable
fun rememberHapticFeedback(context: Context): HapticFeedbackController {
    return remember(context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        HapticFeedbackController(vibrator)
    }
}

class HapticFeedbackController(private val vibrator: Vibrator) {
    fun performHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RCCarControllerTheme(darkTheme = true) {
        MainScreen(
            uiState = RcCarUiState(connectionState = ConnectionState.Connected, lastCommand = 'F'),
            onCommand = {},
            onConnect = {},
            onDismissSnackbar = {}
        )
    }
}
