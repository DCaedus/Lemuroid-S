package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Lemuroid-S: system bars while playing (game screen and in-game menu). The navigation bar is
 * hidden; the status bar is hidden in landscape, so the game uses the whole height, and shown in
 * portrait, where there's room for it (the screens lay themselves out below it).
 */
object GameSystemBars {
    /** The in-game menu (the game is paused): status and navigation bars are shown. */
    fun showAll(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun apply(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
