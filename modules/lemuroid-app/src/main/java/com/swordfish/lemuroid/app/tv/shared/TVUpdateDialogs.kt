package com.swordfish.lemuroid.app.tv.shared

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.swordfish.lemuroid.app.mobile.feature.main.AppUpdateDialogs
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme

/**
 * Lemuroid-S (GitHub version): shows the app update dialogs on a TV screen. The dialogs open in
 * their own window, so the host view takes no space.
 */
object TVUpdateDialogs {
    fun attach(activity: ComponentActivity) {
        val view =
            ComposeView(activity).apply {
                setContent { AppTheme { AppUpdateDialogs() } }
            }
        activity.addContentView(view, ViewGroup.LayoutParams(0, 0))
    }
}
