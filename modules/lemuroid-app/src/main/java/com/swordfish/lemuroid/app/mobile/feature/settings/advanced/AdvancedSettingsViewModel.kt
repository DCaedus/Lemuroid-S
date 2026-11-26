package com.swordfish.lemuroid.app.mobile.feature.settings.advanced

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor

@Suppress("UNUSED_PARAMETER")
class AdvancedSettingsViewModel(
    appContext: Context,
    private val settingsInteractor: SettingsInteractor,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val settingsInteractor: SettingsInteractor,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdvancedSettingsViewModel(appContext, settingsInteractor) as T
        }
    }

    fun resetAllSettings() {
        settingsInteractor.resetAllSettings()
    }
}
