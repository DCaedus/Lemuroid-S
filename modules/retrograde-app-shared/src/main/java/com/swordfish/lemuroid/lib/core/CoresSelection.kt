package com.swordfish.lemuroid.lib.core

import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig

/**
 * Which core runs each system. Lemuroid-S uses one core per system, so it's always the system's
 * core (Lemuroid let some systems switch cores in its settings).
 */
class CoresSelection {
    fun getCoreConfigForSystem(system: GameSystem): SystemCoreConfig = system.systemCoreConfigs.first()
}
