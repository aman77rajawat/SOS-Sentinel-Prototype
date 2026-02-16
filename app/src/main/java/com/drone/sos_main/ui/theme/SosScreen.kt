package com.drone.sos_main.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drone.sos_main.viewmodel.SosViewModel

/**
 * SosScreen owns all SOS UI.
 * MainActivity just calls SosScreen() — nothing else.
 */
@Composable
fun SosScreen(modifier: Modifier = Modifier) {
    val viewModel: SosViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ── Permission check ──────────────────────────────────────────────────
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Ask for permission automatically on first open
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Background animates dark red when SOS is active
    val bgColor by animateColorAsState(
        targetValue = if (uiState.isSosActive) Color(0xFF1A0000) else Color(0xFF0D0D0D),
        animationSpec = tween(500),
        label = "bg"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            text = if (uiState.isSosActive) "SOS ACTIVE" else "Emergency SOS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isSosActive) Color(0xFFFF3333) else Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (uiState.isSosActive)
                "Broadcasting your live location..."
            else
                "Press to send your live location",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Connection badge ──────────────────────────────────────────────
        ConnectionStatusBadge(
            isConnected = uiState.isConnected,
            isConnecting = uiState.isConnecting
        )

        Spacer(modifier = Modifier.height(48.dp))

        // ── SOS Button ────────────────────────────────────────────────────
        SosPulsingButton(
            isActive = uiState.isSosActive,
            onClick = {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    return@SosPulsingButton
                }
                if (uiState.isSosActive) viewModel.cancelSos()
                else viewModel.triggerSos()
            }
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Status message ────────────────────────────────────────────────
        Text(
            text = uiState.statusMessage,
            fontSize = 13.sp,
            color = if (uiState.isSosActive) Color(0xFFFF5252)
            else Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        // ── Permission warning ────────────────────────────────────────────
        if (!hasLocationPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location permission required",
                color = Color(0xFFFFAA00),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // ── Active SOS card ───────────────────────────────────────────────
        if (uiState.isSosActive) {
            Spacer(modifier = Modifier.height(24.dp))
            SosActiveCard(droneInfo = uiState.droneInfo)
        }
    }
}

// ── Pulsing SOS Button ────────────────────────────────────────────────────────
@Composable
fun SosPulsingButton(isActive: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFFCC0000) else Color(0xFFFF1A1A),
        animationSpec = tween(400),
        label = "color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(buttonColor.copy(alpha = 0.15f))
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = CircleShape
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SOS",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (isActive) {
                    Text(
                        text = "TAP TO STOP",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ── Connection Status Badge ───────────────────────────────────────────────────
@Composable
fun ConnectionStatusBadge(isConnected: Boolean, isConnecting: Boolean) {
    val (color, label) = when {
        isConnecting -> Color(0xFFFFA000) to "⏳ Connecting..."
        isConnected  -> Color(0xFF4CAF50) to "● Connected"
        else         -> Color(0xFFF44336) to "● Disconnected"
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── SOS Active Info Card ──────────────────────────────────────────────────────
/**
 * Shows when SOS is active.
 * If a drone has accepted, shows the drone info too.
 */
@Composable
fun SosActiveCard(droneInfo: String?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF3B0000),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🆘 Alert sent to all drones",
                color = Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Live location is being shared every second",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            // Show drone info if a drone has responded
            if (droneInfo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🚁 $droneInfo",
                    color = Color(0xFF4CAF50),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}