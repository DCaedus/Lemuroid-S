package com.swordfish.lemuroid.app.shared.coreoptions

import android.content.Context
import android.util.Base64
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.libretrodroid.Variable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Lemuroid-S: a core only tells its settings (and their possible values) while it runs. They're kept
 * here when a game starts, so Settings > Core settings can show them without running the core.
 */
object CoreOptionsCache {
    private const val KEY_PREFIX = "core_options_cache_"

    fun save(
        context: Context,
        config: SystemCoreConfig,
        variables: Array<Variable>,
    ) {
        val coreOptions = variables.mapNotNull { runCatching { CoreOption.fromLibretroDroidVariable(it) }.getOrNull() }
        val options =
            (config.exposedSettings + config.exposedAdvancedSettings).mapNotNull { setting ->
                coreOptions.firstOrNull { it.variable.key == setting.key }?.let { LemuroidCoreOption(setting, it) }
            }
        if (options.isEmpty()) return

        // Systems sharing a core can expose different settings: keep them all.
        val merged = LinkedHashMap<String, LemuroidCoreOption>()
        load(context, config.coreID).forEach { merged[it.getKey()] = it }
        options.forEach { merged[it.getKey()] = it }

        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(ArrayList(merged.values)) } }
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putString(KEY_PREFIX + config.coreID.coreName, Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
            .apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun load(
        context: Context,
        coreID: CoreID,
    ): List<LemuroidCoreOption> {
        val encoded =
            SharedPreferencesHelper.getSharedPreferences(context).getString(KEY_PREFIX + coreID.coreName, null)
                ?: return emptyList()
        return runCatching {
            ObjectInputStream(ByteArrayInputStream(Base64.decode(encoded, Base64.NO_WRAP))).use {
                it.readObject() as List<LemuroidCoreOption>
            }
        }.getOrDefault(emptyList())
    }
}
