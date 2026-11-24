package com.swordfish.lemuroid.lib.saves

import kotlinx.serialization.Serializable

class SaveState(val state: ByteArray, val metadata: Metadata) {
    @Serializable
    data class Metadata(
        val diskIndex: Int = 0,
        val version: Int = 0,
        // Lemuroid-S: core that made the state. States are kept per system in the Lemuroid-S folder,
        // so this prevents loading a state made by a different core for the same system.
        val coreName: String? = null,
    )
}
