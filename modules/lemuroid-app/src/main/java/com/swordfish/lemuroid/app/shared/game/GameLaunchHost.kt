package com.swordfish.lemuroid.app.shared.game

import android.content.Intent

/**
 * Lemuroid-S: a screen that can start the game screen and get its result back, using Android's
 * activity result API (replaces startActivityForResult/onActivityResult).
 */
interface GameLaunchHost {
    fun launchGameActivity(intent: Intent)
}
