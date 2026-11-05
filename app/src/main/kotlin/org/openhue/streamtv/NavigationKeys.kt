package org.openhue.streamtv

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Setup : NavKey
@Serializable data object Main : NavKey
@Serializable data object Whitelist : NavKey
