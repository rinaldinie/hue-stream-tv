package org.openhue.streamtv

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.openhue.streamtv.hue.repository.HueRepository
import org.openhue.streamtv.ui.main.MainScreen
import org.openhue.streamtv.ui.setup.SetupScreen
import org.openhue.streamtv.ui.whitelist.WhitelistScreen

@Composable
fun MainNavigation(
    onToggleCapture: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repo = remember { HueRepository(context) }
    val initialKey = if (repo.getSavedCredentials() != null && repo.getSelectedGroupId() != null) Main else Setup
    val backStack = rememberNavBackStack(initialKey)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                activity?.finish()
            }
        },
        entryProvider = entryProvider {
            entry<Setup> {
                SetupScreen(
                    onSetupComplete = {
                        backStack.clear()
                        backStack.add(Main)
                    },
                    onExit = { activity?.finish() }
                )
            }
            entry<Main> {
                MainScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onToggleCapture = onToggleCapture,
                    onExit = { activity?.finish() }
                )
            }
            entry<Whitelist> {
                WhitelistScreen(
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
