package org.openhue.streamtv.hue.model

data class HueBridge(
    val id: String,
    val ip: String,
    val name: String = "Philips Hue Bridge",
    val modelId: String? = null
)

data class HueCredentials(
    val bridgeIp: String,
    val bridgeId: String,
    val username: String,
    val clientKey: String
)

data class LightLocation(
    val x: Double,
    val y: Double,
    val z: Double
)

data class HueGroup(
    val id: String,
    val name: String,
    val type: String,
    val lightIds: List<String>,
    val locations: Map<String, LightLocation>
) {
    val isEntertainment: Boolean get() = type.equals("Entertainment", ignoreCase = true)
}

sealed class PairingState {
    object Idle : PairingState()
    data class WaitingForButton(val secondsLeft: Int) : PairingState()
    data class Success(val credentials: HueCredentials) : PairingState()
    data class Error(val message: String) : PairingState()
}
