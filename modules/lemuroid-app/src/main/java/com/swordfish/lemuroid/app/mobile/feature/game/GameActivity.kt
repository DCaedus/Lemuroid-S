package com.swordfish.lemuroid.app.mobile.feature.game

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.settings.OrientationSettings

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

    /** Lemuroid-S: in-game "Screen orientation"; works even when Android's auto-rotate is locked. */
    override fun applyOrientationSetting() {
        requestedOrientation = OrientationSettings.gameOrientation(this)
    }
}
