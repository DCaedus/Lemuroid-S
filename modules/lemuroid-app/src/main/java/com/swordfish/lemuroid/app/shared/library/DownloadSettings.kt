package com.swordfish.lemuroid.app.shared.library

import com.swordfish.lemuroid.app.shared.covers.CoverStore
import androidx.core.content.edit
import android.content.Context
import android.net.ConnectivityManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Lemuroid-S: "Data saver" (on by default). Cores downloaded after a scan
 * or at start-up, and covers fetched as games appear on screen, then wait for Wi-Fi. What the user
 * asks for directly (starting a game, "Download cores and covers") is not held back.
 */
object DownloadSettings {
    const val PREF_WIFI_ONLY = "downloads_wifi_only"
    const val DEFAULT_WIFI_ONLY = true

    /** Tag of downloads waiting for Wi-Fi: they don't count as running operations meanwhile. */
    const val WAITS_FOR_WIFI_TAG = "lemuroid_waits_for_wifi"

    /** Set when the user chose in the first-launch setup's Data saver card. */
    private const val PREF_CHOSEN = "downloads_wifi_only_chosen"

    /** While the setup's Data saver card is waiting for an answer, nothing is scanned or downloaded. */
    private const val PREF_CHOICE_PENDING = "downloads_wifi_only_choice_pending"
    private const val PREF_SCAN_DEFERRED = "downloads_wifi_only_scan_deferred"

    private fun preferences(context: Context) = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    fun isChosen(context: Context): Boolean = preferences(context).getBoolean(PREF_CHOSEN, false)

    fun isChoicePending(context: Context): Boolean = preferences(context).getBoolean(PREF_CHOICE_PENDING, false)

    /** The first-launch setup shows the Data saver card: hold scans and downloads until it's answered. */
    fun holdUntilChosen(context: Context) {
        if (!isChosen(context)) preferences(context).edit { putBoolean(PREF_CHOICE_PENDING, true) }
    }

    /** A scan asked for while waiting; it runs once the card is answered. */
    fun deferScan(context: Context) {
        preferences(context).edit { putBoolean(PREF_SCAN_DEFERRED, true) }
    }

    /** The answer from the setup card: saves it and starts what was held back. */
    fun choose(
        context: Context,
        wifiOnly: Boolean,
    ) {
        val preferences = preferences(context)
        val scanDeferred = preferences.getBoolean(PREF_SCAN_DEFERRED, false)
        preferences.edit {
            putBoolean(PREF_WIFI_ONLY, wifiOnly)
            putBoolean(PREF_CHOSEN, true)
            putBoolean(PREF_CHOICE_PENDING, false)
            putBoolean(PREF_SCAN_DEFERRED, false)
        }
        val appContext = context.applicationContext
        if (scanDeferred) LibraryIndexScheduler.scheduleLibrarySync(appContext)
        LibraryIndexScheduler.cancelCoreUpdate(appContext)
        LibraryIndexScheduler.scheduleCoreUpdate(appContext)
        // Artwork shown meanwhile is looked up again, now that it may be downloaded.
        CoverStore.get(appContext).forgetMainFolderListings()
    }

    fun wifiOnly(context: Context): Boolean =
        SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
            .getBoolean(PREF_WIFI_ONLY, DEFAULT_WIFI_ONLY)

    /** Whether automatic downloads may run now. */
    fun automaticDownloadsAllowed(context: Context): Boolean {
        if (isChoicePending(context)) return false
        if (!wifiOnly(context)) return true
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        return connectivity != null && !connectivity.isActiveNetworkMetered
    }
}
