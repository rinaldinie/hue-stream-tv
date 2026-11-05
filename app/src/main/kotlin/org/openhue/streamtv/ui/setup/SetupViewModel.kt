package org.openhue.streamtv.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhue.streamtv.hue.auth.HueAuth
import org.openhue.streamtv.hue.discovery.HueDiscovery
import org.openhue.streamtv.hue.model.HueBridge
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.model.HueGroup
import org.openhue.streamtv.hue.model.PairingState
import org.openhue.streamtv.hue.repository.HueRepository

sealed class SetupStep {
    object Discovery : SetupStep()
    data class Pairing(val bridge: HueBridge, val secondsLeft: Int) : SetupStep()
    data class AreaSelection(val credentials: HueCredentials, val areas: List<HueGroup>, val selectedId: String?) : SetupStep()
    data class Ready(val credentials: HueCredentials, val selectedArea: HueGroup) : SetupStep()
    data class Error(val message: String) : SetupStep()
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = HueDiscovery(application)
    private val auth = HueAuth()
    private val repository = HueRepository(application)

    private val _step = MutableStateFlow<SetupStep>(SetupStep.Discovery)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _discoveredBridges = MutableStateFlow<List<HueBridge>>(emptyList())
    val discoveredBridges: StateFlow<List<HueBridge>> = _discoveredBridges.asStateFlow()

    init {
        checkSavedCredentials()
    }

    private fun checkSavedCredentials() {
        val saved = repository.getSavedCredentials()
        if (saved != null) {
            loadAreas(saved)
        } else {
            startDiscovery()
        }
    }

    fun startDiscovery() {
        viewModelScope.launch {
            _isSearching.value = true
            _step.value = SetupStep.Discovery
            try {
                val list = discovery.discoverAll()
                _discoveredBridges.value = list
                if (list.isEmpty()) {
                    _step.value = SetupStep.Error("No Philips Hue Bridge found on the local network. Make sure your Bridge is powered on and connected to the same Wi-Fi or Ethernet network.")
                }
            } catch (e: Exception) {
                _step.value = SetupStep.Error("Error during bridge discovery: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectBridge(bridge: HueBridge) {
        viewModelScope.launch {
            auth.pairWithBridge(bridge.ip, bridge.id).collect { state ->
                when (state) {
                    is PairingState.WaitingForButton -> {
                        _step.value = SetupStep.Pairing(bridge, state.secondsLeft)
                    }
                    is PairingState.Success -> {
                        repository.saveCredentials(state.credentials)
                        loadAreas(state.credentials)
                    }
                    is PairingState.Error -> {
                        _step.value = SetupStep.Error(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadAreas(creds: HueCredentials) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val groups = repository.getEntertainmentGroups(creds)
                val savedGroupId = repository.getSelectedGroupId()
                if (groups.isNotEmpty()) {
                    val activeId = savedGroupId ?: groups.first().id
                    _step.value = SetupStep.AreaSelection(creds, groups, activeId)
                } else {
                    _step.value = SetupStep.Error("No Entertainment Area found on this Bridge. Please create an Entertainment Area in the official Philips Hue mobile app first.")
                }
            } catch (e: Exception) {
                _step.value = SetupStep.Error("Unable to load Entertainment Areas: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectArea(groupId: String) {
        val current = _step.value
        if (current is SetupStep.AreaSelection) {
            repository.setSelectedGroupId(groupId)
            _step.value = current.copy(selectedId = groupId)
        }
    }

    fun confirmAreaSelection(onReady: () -> Unit) {
        val current = _step.value
        if (current is SetupStep.AreaSelection && current.selectedId != null) {
            val selected = current.areas.firstOrNull { it.id == current.selectedId }
            if (selected != null) {
                _step.value = SetupStep.Ready(current.credentials, selected)
                onReady()
            }
        }
    }

    fun disconnectBridge() {
        repository.clearCredentials()
        _discoveredBridges.value = emptyList()
        _step.value = SetupStep.Discovery
        startDiscovery()
    }

    fun enableDemoMode(onReady: () -> Unit) {
        val demoCreds = HueCredentials(
            bridgeIp = "127.0.0.1",
            bridgeId = "001788FFFE000001",
            username = "demo-token",
            clientKey = "0123456789abcdef0123456789abcdef"
        )
        repository.saveCredentials(demoCreds)
        repository.setSelectedGroupId("demo-area-1")
        onReady()
    }
}
