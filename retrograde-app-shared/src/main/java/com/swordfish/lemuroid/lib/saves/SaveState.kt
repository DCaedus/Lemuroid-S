package com.swordfish.lemuroid.lib.saves

import kotlinx.serialization.Serializable

class SaveState(val state: ByteArray, val metadata: Metadata) {
    @Serializable
    data class Metadata(
        val diskIndex: Int = 0,
        val version: Int = 0,
        // Lemuroid-S: core that made the state (informational; states are kept per system).
        val coreName: String? = null,
    )
}
