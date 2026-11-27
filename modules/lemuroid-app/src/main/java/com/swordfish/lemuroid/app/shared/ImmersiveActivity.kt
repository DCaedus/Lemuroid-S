package com.swordfish.lemuroid.app.shared

import android.content.res.Configuration
import android.view.View
import com.swordfish.lemuroid.lib.android.RetrogradeActivity

abstract class ImmersiveActivity : RetrogradeActivity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
    }

    /** Hides the system bars; the game screen overrides it (see GameSystemBars). */
    protected open fun applySystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
}
