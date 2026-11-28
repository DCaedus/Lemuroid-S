package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import android.net.ConnectivityManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Lemuroid-S: "Automatic downloads over Wi-Fi only" (on by default). Cores downloaded after a scan
 * or at start-up, and covers fetched as games appear on screen, then wait for Wi-Fi. What the user
 * asks for directly (starting a game, "Download cores and covers") is not held back.
 */
object DownloadSettings {
    const val PREF_WIFI_ONLY = "downloads_wifi_only"
    const val DEFAULT_WIFI_ONLY = true

    /** Tag of downloads waiting for Wi-Fi: they don't count as running operations meanwhile. */
    const val WAITS_FOR_WIFI_TAG = "lemuroid_waits_for_wifi"

    fun wifiOnly(context: Context): Boolean =
        SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
            .getBoolean(PREF_WIFI_ONLY, DEFAULT_WIFI_ONLY)

    /** Whether automatic downloads may run now. */
    fun automaticDownloadsAllowed(context: Context): Boolean {
        if (!wifiOnly(context)) return true
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        return connectivity != null && !connectivity.isActiveNetworkMetered
    }
}
