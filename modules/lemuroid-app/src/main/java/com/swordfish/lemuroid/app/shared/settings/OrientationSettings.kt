package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Lemuroid-S screen orientation settings (Settings > Display / Interface and the game menu).
 * The fixed choices apply even when Android's auto-rotate is locked.
 */
object OrientationSettings {
    const val SYSTEM = "system"
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
    const val REVERSE_LANDSCAPE = "reverse_landscape"

    private fun preferences(context: Context): SharedPreferences =
        SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    /** Orientation for the game screen. */
    fun gameOrientation(context: Context): Int =
        when (preferences(context).getString(context.getString(R.string.pref_key_game_orientation), SYSTEM)) {
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    /** Orientation for the app's menus: portrait if "Menus always in portrait" is on. */
    fun menuOrientation(context: Context): Int =
        if (preferences(context).getBoolean(context.getString(R.string.pref_key_menus_portrait), false)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    /** Keeps [activity]'s menus in the chosen orientation, also when the setting changes. */
    fun followMenuOrientation(activity: Activity): SharedPreferences.OnSharedPreferenceChangeListener {
        apply(activity)
        val key = activity.getString(R.string.pref_key_menus_portrait)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) apply(activity)
            }
        preferences(activity).registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    // Android 8.0 refuses fixed orientations for see-through screens such as the game menu.
    private fun apply(activity: Activity) {
        runCatching { activity.requestedOrientation = menuOrientation(activity) }
    }

    fun stopFollowing(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = preferences(context).unregisterOnSharedPreferenceChangeListener(listener)
}
