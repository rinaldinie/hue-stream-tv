package org.openhue.streamtv.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlin.math.roundToInt
import org.openhue.streamtv.Setup
import org.openhue.streamtv.Whitelist
import org.openhue.streamtv.capture.SyncDynamics
import org.openhue.streamtv.ui.components.DpadCard

@Composable
fun MainScreen(
    onNavigate: (NavKey) -> Unit,
    onToggleCapture: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val heroFocusRequester = remember { FocusRequester() }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Intercept Back button to dismiss help dialog if open
    BackHandler(enabled = showHelpDialog) {
        showHelpDialog = false
    }

    LaunchedEffect(Unit) {
        viewModel.refreshState()
        kotlinx.coroutines.delay(150)
        try {
            heroFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(showHelpDialog) {
        if (!showHelpDialog) {
            kotlinx.coroutines.delay(50)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D))
    ) {
        // Main Dashboard Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Hue Stream TV",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ambient Screen Sync for Android TV",
                            fontSize = 13.sp,
                            color = Color(0xFF757575)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val areaName = uiState.selectedGroup?.name ?: "Entertainment Area"
                        val ip = uiState.credentials?.bridgeIp ?: "Not connected"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161A22))
                                .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$areaName ($ip)",
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }

                        DpadCard(
                            onClick = { showHelpDialog = true },
                            backgroundColor = Color(0xFF151C28),
                            focusedBorderColor = Color(0xFF00E5FF)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "?",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Help",
                                    color = Color(0xFFB0C4DE),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        DpadCard(
                            onClick = onExit,
                            backgroundColor = Color(0xFF1E212D),
                            focusedBorderColor = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "✕ Exit",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            item {
                val isRunning = uiState.isRunning
                val isPaused = uiState.isPausedByWhitelist
                val hasWhitelist = uiState.whitelistedAppsCount > 0

                val statusTitle = when {
                    !isRunning -> "START SCREEN SYNC"
                    isPaused -> "AUTO-PAUSED (APP NOT WHITELISTED)"
                    else -> "SCREEN SYNC ACTIVE"
                }

                val statusSubtitle = when {
                    !isRunning && !hasWhitelist -> "Global sync: press START to sync screen colors across any TV app or content"
                    !isRunning && hasWhitelist -> "Press START to begin. Screen sync activates in the ${uiState.whitelistedAppsCount} whitelisted apps"
                    isPaused -> "Lights released. Open a whitelisted app to automatically resume streaming"
                    else -> "Streaming screen colors via DTLS. Press STOP to end service"
                }

                val glowColor = when {
                    !isRunning -> Color(0xFF232936)
                    isPaused -> Color(0xFFFFB300)
                    else -> Color(0xFF00E5FF)
                }

                DpadCard(
                    onClick = onToggleCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .focusRequester(heroFocusRequester)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isRunning) Color(0xFF101C26) else Color(0xFF141821))
                            .border(2.dp, glowColor, RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(glowColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = statusTitle,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRunning) Color.White else Color(0xFFE0E0E0)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = statusSubtitle,
                                    fontSize = 13.sp,
                                    color = Color(0xFF9E9E9E)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isRunning) Color(0xFFD32F2F) else Color(0xFF00E5FF))
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (isRunning) "STOP" else "START",
                                    color = if (isRunning) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val whitelistCount = uiState.whitelistedAppsCount
                    DpadCard(
                        onClick = { onNavigate(Whitelist) },
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF131720))
                                .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "App Whitelist",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (whitelistCount == 0) "0 apps selected (Sync globally everywhere)" else "$whitelistCount apps enabled (Filter active)",
                                    fontSize = 13.sp,
                                    color = Color(0xFF00E5FF)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Click to select authorized streaming apps",
                                    fontSize = 11.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        }
                    }

                    val isAutoReleaseOn = uiState.isAutoReleaseEnabled
                    DpadCard(
                        onClick = {
                            viewModel.toggleAutoRelease()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF131720))
                                .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isAutoReleaseOn) Color(0xFF4CAF50) else Color(0xFF888888))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Auto-Release Service",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isAutoReleaseOn) "Enabled (Auto-pauses when switching apps)" else "Disabled (Sync continues everywhere)",
                                    fontSize = 13.sp,
                                    color = if (isAutoReleaseOn) Color(0xFF81C784) else Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAutoReleaseOn) "Active • Press OK to disable" else "Inactive • Press OK to enable",
                                    fontSize = 11.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = "Sync Dynamics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SyncDynamics.values().forEach { dynamics ->
                            val selected = uiState.dynamics == dynamics
                            DynamicsOptionCard(
                                title = dynamics.label,
                                subtitle = when (dynamics) {
                                    SyncDynamics.SUBTLE -> "Smooth & cinematic"
                                    SyncDynamics.BALANCED -> "Balanced (Recommended)"
                                    SyncDynamics.INTENSE -> "Ultra-fast action"
                                },
                                isSelected = selected,
                                onClick = { viewModel.setDynamics(dynamics) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                BrightnessSliderCard(
                    brightnessPercent = uiState.brightnessPercent,
                    onBrightnessChange = { viewModel.setBrightnessPercent(it) }
                )
            }

            item {
                val isDemo = uiState.credentials?.username == "demo-token"
                val buttonText = if (isDemo) {
                    "✨ Exit Demo Mode & search for physical Bridge"
                } else {
                    "Disconnect Bridge / Change Entertainment Area"
                }

                DpadCard(
                    onClick = {
                        viewModel.resetBridge()
                        onNavigate(Setup)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    focusedBorderColor = if (isDemo) Color(0xFF00E5FF) else Color(0xFF3D72FF)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDemo) Color(0xFF10202A) else Color(0xFF141720))
                            .border(1.dp, if (isDemo) Color(0xFF005B66) else Color(0xFF262D3D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = buttonText,
                            fontSize = 13.sp,
                            fontWeight = if (isDemo) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isDemo) Color(0xFF00E5FF) else Color(0xFF888888)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Help & FAQ Modal Overlay
        AnimatedVisibility(
            visible = showHelpDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HelpFaqModal(onDismiss = { showHelpDialog = false })
        }
    }
}

@Composable
fun HelpFaqModal(
    onDismiss: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            closeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 80.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF10131B))
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {} // Prevent click from passing to backdrop
                .padding(28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ℹ️",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "How Hue Stream TV Works",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    DpadCard(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocusRequester),
                        backgroundColor = Color(0xFF1E2434),
                        focusedBorderColor = Color(0xFF00E5FF)
                    ) {
                        Text(
                            text = "✕ Close",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF262D3D))
                )

                // FAQ Item 1
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• What happens if I press START without apps in Whitelist?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Screen sync is global: Hue lights will mirror screen colors across any TV app, movie, video, or game without restrictions.",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E),
                        lineHeight = 18.sp
                    )
                }

                // FAQ Item 2
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• What happens if apps are in Whitelist but the app is NOT in START?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "The sync engine is completely idle: zero CPU consumption, no background screen capture, and your Philips Hue lights remain untouched for everyday lighting.",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E),
                        lineHeight = 18.sp
                    )
                }

                // FAQ Item 3
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• What does the Auto-Release Service do?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "It is the TV accessibility service that detects in real-time when you exit a whitelisted app or return to the TV Home launcher: it immediately pauses screen capture and restores your Philips Hue lights to their original state, then resumes streaming automatically when you reopen the app.",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E),
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Press Back or select Close to return to dashboard.",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
fun BrightnessSliderCard(
    brightnessPercent: Int,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) Color(0xFF00E5FF) else Color(0xFF262D3D)
    val bgColor = if (isFocused) Color(0xFF161E2C) else Color(0xFF131720)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val next = (brightnessPercent - 5).coerceIn(20, 150)
                            onBrightnessChange(next)
                            true
                        }
                        Key.DirectionRight -> {
                            val next = (brightnessPercent + 5).coerceIn(20, 150)
                            onBrightnessChange(next)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Light Brightness",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isFocused) "← / → to adjust" else "",
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F2B38))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    val label = if (brightnessPercent > 100) "$brightnessPercent% (Boost)" else "$brightnessPercent%"
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minus Step Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2433))
                        .border(1.dp, Color(0xFF2C3549), RoundedCornerShape(8.dp))
                        .clickable {
                            val next = (brightnessPercent - 10).coerceIn(20, 150)
                            onBrightnessChange(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "−", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Slider Track
                Slider(
                    value = brightnessPercent.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = 20f..150f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0xFF262D3D)
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Plus Step Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2433))
                        .border(1.dp, Color(0xFF2C3549), RoundedCornerShape(8.dp))
                        .clickable {
                            val next = (brightnessPercent + 10).coerceIn(20, 150)
                            onBrightnessChange(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Adjust with remote ← / → arrow keys, or click/drag with mouse.",
                fontSize = 11.sp,
                color = Color(0xFF757575)
            )
        }
    }
}

@Composable
fun DynamicsOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isFocused -> Color(0xFF00E5FF)
        isSelected -> Color(0xFF00B4D8)
        else -> Color(0xFF262D3D)
    }

    val bgColor = when {
        isFocused -> Color(0xFF1C2230)
        isSelected -> Color(0xFF12202E)
        else -> Color(0xFF131720)
    }

    Box(
        modifier = modifier
            .height(68.dp)
            .scale(if (isFocused) 1.03f else 1.0f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF888888)
            )
        }
    }
}
