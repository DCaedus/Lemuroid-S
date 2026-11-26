package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class GameActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientationSetting()
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java

    /**
     * Lemuroid-S: in-game "Screen orientation" (game menu). Portrait or landscape apply even when
     * Android's auto-rotate is locked; Auto follows the phone as usual.
     */
    override fun applyOrientationSetting() {
        val value =
            SharedPreferencesHelper.getSharedPreferences(applicationContext)
                .getString(getString(R.string.pref_key_game_orientation), ORIENTATION_AUTO)
        requestedOrientation =
            when (value) {
                ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
    }

    companion object {
        const val ORIENTATION_AUTO = "auto"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_LANDSCAPE = "landscape"
    }
}
