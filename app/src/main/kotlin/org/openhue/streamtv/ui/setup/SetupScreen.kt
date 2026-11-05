package org.openhue.streamtv.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openhue.streamtv.hue.model.HueBridge
import org.openhue.streamtv.hue.model.HueGroup
import org.openhue.streamtv.ui.components.DpadCard

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel()
) {
    val step by viewModel.step.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val discoveredBridges by viewModel.discoveredBridges.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(step = step, onExit = onExit)

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val currentStep = step) {
                    is SetupStep.Discovery -> {
                        DiscoveryContent(
                            isSearching = isSearching,
                            bridges = discoveredBridges,
                            onRefresh = { viewModel.startDiscovery() },
                            onSelectBridge = { viewModel.selectBridge(it) },
                            onDemoMode = { viewModel.enableDemoMode(onSetupComplete) }
                        )
                    }
                    is SetupStep.Pairing -> {
                        PairingContent(
                            bridge = currentStep.bridge,
                            secondsLeft = currentStep.secondsLeft,
                            onCancel = { viewModel.startDiscovery() }
                        )
                    }
                    is SetupStep.AreaSelection -> {
                        AreaSelectionContent(
                            areas = currentStep.areas,
                            selectedId = currentStep.selectedId,
                            onSelectArea = { viewModel.selectArea(it) },
                            onConfirm = { viewModel.confirmAreaSelection(onSetupComplete) },
                            onDisconnect = { viewModel.disconnectBridge() }
                        )
                    }
                    is SetupStep.Ready -> {
                        onSetupComplete()
                    }
                    is SetupStep.Error -> {
                        ErrorContent(
                            message = currentStep.message,
                            onRetry = { viewModel.startDiscovery() },
                            onDemoMode = { viewModel.enableDemoMode(onSetupComplete) },
                            onExit = onExit
                        )
                    }
                }
            }

            BottomDpadHints()
        }
    }
}

@Composable
private fun HeaderBar(
    step: SetupStep,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "HUE STREAM TV",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Open Source Philips Hue Ambient Sync for Android TV",
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val (badgeText, badgeColor) = when (step) {
                is SetupStep.Discovery -> "BRIDGE SEARCH" to Color(0xFF38BDF8)
                is SetupStep.Pairing -> "PAIRING IN PROGRESS" to Color(0xFFF59E0B)
                is SetupStep.AreaSelection, is SetupStep.Ready -> "BRIDGE CONNECTED" to Color(0xFF10B981)
                is SetupStep.Error -> "ATTENTION" to Color(0xFFEF4444)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF141724), shape = CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(badgeColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badgeText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Exit Button
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

@Composable
private fun DiscoveryContent(
    isSearching: Boolean,
    bridges: List<HueBridge>,
    onRefresh: () -> Unit,
    onSelectBridge: (HueBridge) -> Unit,
    onDemoMode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSearching) {
            CircularProgressIndicator(
                color = Color(0xFF3D72FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Searching for Philips Hue Bridge...",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Scanning local network via mDNS and UPnP",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            DpadCard(
                onClick = onDemoMode,
                backgroundColor = Color(0xFF0F1E2E),
                focusedBorderColor = Color(0xFF00E5FF)
            ) {
                Text(
                    text = "✨ Skip & Try in Offline Demo Mode",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        } else if (bridges.isNotEmpty()) {
            Text(
                text = "Bridges discovered on local network",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select a bridge to pair using your remote",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                items(bridges) { bridge ->
                    DpadCard(onClick = { onSelectBridge(bridge) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF202538), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💡", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bridge.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "IP: ${bridge.ip} • ID: ${bridge.id}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Connect ➔",
                                color = Color(0xFF3D72FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DpadCard(
                        onClick = onRefresh,
                        backgroundColor = Color(0xFF181B28)
                    ) {
                        Text(
                            text = "Refresh Search",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingContent(
    bridge: HueBridge,
    secondsLeft: Int,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(0.55f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            CircularProgressIndicator(
                progress = { secondsLeft / 30f },
                color = Color(0xFFF59E0B),
                trackColor = Color(0xFF232738),
                strokeWidth = 4.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "${secondsLeft}s",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Press the round button on your Bridge",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Walk over to your Philips Hue Bridge (${bridge.ip}) and press the large center button to authorize this TV.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        DpadCard(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(0.5f),
            backgroundColor = Color(0xFF1E212E)
        ) {
            Text(
                text = "Cancel Pairing",
                color = Color(0xFFEF4444),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AreaSelectionContent(
    areas: List<HueGroup>,
    selectedId: String?,
    onSelectArea: (String) -> Unit,
    onConfirm: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(0.6f)
    ) {
        Text(
            text = "Select Entertainment Area",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Choose the light layout configured for this TV",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(areas) { area ->
                val isSelected = area.id == selectedId
                DpadCard(
                    onClick = { onSelectArea(area.id) },
                    focusedBorderColor = Color(0xFF10B981),
                    backgroundColor = if (isSelected) Color(0xFF142422) else Color(0xFF141724)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) "●" else "○",
                            color = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B),
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = area.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${area.lightIds.size} lights configured",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DpadCard(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF1E3A8A),
                focusedBorderColor = Color(0xFF38BDF8)
            ) {
                Text(
                    text = "Confirm Selection ➔",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    textAlign = TextAlign.Center
                )
            }

            DpadCard(
                onClick = onDisconnect,
                modifier = Modifier.weight(0.5f),
                backgroundColor = Color(0xFF231C24),
                focusedBorderColor = Color(0xFFEF4444)
            ) {
                Text(
                    text = "Disconnect",
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDemoMode: () -> Unit,
    onExit: () -> Unit
) {
    val demoFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            demoFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(0.65f)
    ) {
        Text("⚠️", fontSize = 42.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No Philips Hue Bridge Detected",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        DpadCard(
            onClick = onDemoMode,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .focusRequester(demoFocusRequester),
            backgroundColor = Color(0xFF0F2633),
            focusedBorderColor = Color(0xFF00E5FF)
        ) {
            Text(
                text = "✨ Try in Offline Demo Mode (No Hub Required)",
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.65f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DpadCard(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF1E293B)
            ) {
                Text(
                    text = "Retry Search",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }

            DpadCard(
                onClick = onExit,
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFF261919),
                focusedBorderColor = Color(0xFFEF4444)
            ) {
                Text(
                    text = "Exit App",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BottomDpadHints() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "▲▼ Arrow: Navigate   •   [OK] Center / Enter: Confirm   •   [Back] / Esc: Exit",
            color = Color(0xFF475569),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
    }
}
