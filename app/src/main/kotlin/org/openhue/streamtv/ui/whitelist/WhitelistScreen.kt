package org.openhue.streamtv.ui.whitelist

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openhue.streamtv.ui.components.DpadCard
import org.openhue.streamtv.whitelist.AppItem
import org.openhue.streamtv.whitelist.WhitelistManager

@Composable
fun WhitelistScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val whitelistManager = remember { WhitelistManager(context) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            whitelistManager.getInstalledLaunchableApps()
        }
        apps = loaded
        isLoading = false
    }

    val selectedCount = apps.count { it.isWhitelisted }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "App Whitelist",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Choose which apps activate screen sync. Leaving these apps releases your lights.",
                        fontSize = 13.sp,
                        color = Color(0xFF8A909E)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedCount > 0) {
                        DpadCard(
                            onClick = {
                                apps.forEach { app ->
                                    if (app.isWhitelisted) {
                                        whitelistManager.setPackageWhitelisted(app.packageName, false)
                                    }
                                }
                                apps = apps.map { it.copy(isWhitelisted = false) }
                            },
                            backgroundColor = Color(0xFF1E212D),
                            focusedBorderColor = Color(0xFFFFB300)
                        ) {
                            Text(
                                text = "Deselect All",
                                color = Color(0xFFFFB300),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }

                    DpadCard(
                        onClick = onNavigateBack,
                        backgroundColor = Color(0xFF161A24),
                        focusedBorderColor = Color(0xFF00E5FF)
                    ) {
                        Text(
                            text = "← Back",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedCount == 0) Color(0xFF131D2A) else Color(0xFF0F2321))
                    .border(1.dp, if (selectedCount == 0) Color(0xFF1E3A5F) else Color(0xFF005B66), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (selectedCount == 0) {
                        "ℹ️ No apps selected: global sync is active across the entire TV. Select one or more apps to restrict screen sync to them."
                    } else {
                        "✓ $selectedCount apps selected: Hue lights will sync ONLY while using these apps and will auto-release upon leaving."
                    },
                    fontSize = 12.sp,
                    color = if (selectedCount == 0) Color(0xFF7CB9FF) else Color(0xFF00E5FF)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading installed TV apps...", color = Color(0xFF888888))
                }
            } else if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No compatible TV apps found on this device", color = Color(0xFF888888))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        CompactAppWhitelistItem(
                            app = app,
                            onToggle = { isChecked ->
                                whitelistManager.setPackageWhitelisted(app.packageName, isChecked)
                                apps = apps.map {
                                    if (it.packageName == app.packageName) it.copy(isWhitelisted = isChecked) else it
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactAppWhitelistItem(
    app: AppItem,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale = if (isFocused) 1.02f else 1.0f
    val borderColor = if (isFocused) Color(0xFF00E5FF) else if (app.isWhitelisted) Color(0xFF005B66) else Color(0xFF1E232F)
    val bgColor = if (isFocused) Color(0xFF1C2232) else if (app.isWhitelisted) Color(0xFF121B24) else Color(0xFF12141A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isFocused) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                onToggle(!app.isWhitelisted)
            }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            val iconBitmap = (app.icon as? BitmapDrawable)?.bitmap
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF262C3A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.isWhitelisted) Color.White else Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = app.isWhitelisted,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.75f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00E5FF),
                checkedTrackColor = Color(0xFF005B66),
                uncheckedThumbColor = Color(0xFF6B7280),
                uncheckedTrackColor = Color(0xFF1E232F)
            )
        )
    }
}
