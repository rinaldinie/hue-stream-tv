package org.openhue.streamtv.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhue.streamtv.capture.ScreenCaptureService
import org.openhue.streamtv.capture.SyncDynamics
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.model.HueGroup
import org.openhue.streamtv.hue.repository.HueRepository
import org.openhue.streamtv.whitelist.HueAccessibilityService
import org.openhue.streamtv.whitelist.WhitelistManager

data class MainUiState(
    val credentials: HueCredentials? = null,
    val selectedGroup: HueGroup? = null,
    val isRunning: Boolean = false,
    val isPausedByWhitelist: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isAutoReleaseEnabled: Boolean = true,
    val whitelistedAppsCount: Int = 0,
    val dynamics: SyncDynamics = SyncDynamics.BALANCED,
    val brightnessPercent: Int = 100
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HueRepository(application)
    private val whitelistManager = WhitelistManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshState()
        observeServiceState()
    }

    fun refreshState() {
        val creds = repository.getSavedCredentials()
        val groupId = repository.getSelectedGroupId()
        val isA11y = HueAccessibilityService.isAccessibilityEnabled(getApplication())
        val whitelistCount = whitelistManager.getWhitelistedPackages().size
        val dynName = repository.getDynamicsName()
        val dynamics = try { SyncDynamics.valueOf(dynName) } catch (_: Exception) { SyncDynamics.BALANCED }
        val brightness = repository.getBrightnessPercent()
        val autoRelease = repository.isAutoReleaseEnabled()

        ScreenCaptureService.currentDynamics = dynamics
        ScreenCaptureService.currentBrightnessMultiplier = brightness / 100f

        _uiState.value = _uiState.value.copy(
            credentials = creds,
            isAccessibilityEnabled = isA11y,
            isAutoReleaseEnabled = autoRelease,
            whitelistedAppsCount = whitelistCount,
            dynamics = dynamics,
            brightnessPercent = brightness
        )

        if (creds != null && groupId != null) {
            viewModelScope.launch {
                val groups = repository.getEntertainmentGroups(creds)
                val group = groups.firstOrNull { it.id == groupId }
                _uiState.value = _uiState.value.copy(selectedGroup = group)
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            ScreenCaptureService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isRunning = running)
            }
        }
        viewModelScope.launch {
            ScreenCaptureService.isPausedByWhitelist.collect { paused ->
                _uiState.value = _uiState.value.copy(isPausedByWhitelist = paused)
            }
        }
    }

    fun toggleAutoRelease() {
        val next = !_uiState.value.isAutoReleaseEnabled
        repository.setAutoReleaseEnabled(next)
        HueAccessibilityService.setAccessibilityServiceEnabled(getApplication(), next)
        val isA11y = HueAccessibilityService.isAccessibilityEnabled(getApplication())
        _uiState.value = _uiState.value.copy(
            isAutoReleaseEnabled = next,
            isAccessibilityEnabled = isA11y
        )
    }

    fun setDynamics(dynamics: SyncDynamics) {
        repository.setDynamicsName(dynamics.name)
        ScreenCaptureService.currentDynamics = dynamics
        _uiState.value = _uiState.value.copy(dynamics = dynamics)
    }

    fun setBrightnessPercent(percent: Int) {
        val clamped = percent.coerceIn(10, 150)
        repository.setBrightnessPercent(clamped)
        val factor = clamped / 100f
        ScreenCaptureService.currentBrightnessMultiplier = factor
        _uiState.value = _uiState.value.copy(brightnessPercent = clamped)
    }

    fun resetBridge() {
        repository.clearCredentials()
    }
}
