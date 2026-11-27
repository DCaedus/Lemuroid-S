package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.InputDevice
import android.view.View
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import com.swordfish.lemuroid.app.mobile.feature.main.AppUpdateRequests
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsCache
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.tv.setup.TVSetupActivity
import com.swordfish.lemuroid.ext.feature.update.AppUpdater
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SaveSyncPreferences
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.kotlin.NTuple2
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class TVSettingsFragment : LeanbackPreferenceFragmentCompat() {
    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var biosPreferences: BiosPreferences

    @Inject
    lateinit var gamePadPreferencesHelper: GamePadPreferencesHelper

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var coresSelectionPreferences: CoresSelectionPreferences

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    lateinit var saveSyncPreferences: SaveSyncPreferences

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)

        saveSyncPreferences = SaveSyncPreferences(saveSyncManager)

        super.onAttach(context)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        launchOnState(Lifecycle.State.CREATED) {
            val gamePadStatus =
                combine(
                    inputDeviceManager.getGamePadsObservable(),
                    inputDeviceManager.getEnabledInputsObservable(),
                    ::NTuple2,
                )

            gamePadStatus
                .distinctUntilChanged()
                .collect { (pads, enabledPads) -> addGamePadBindingsScreen(pads, enabledPads) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            inputDeviceManager.getEnabledInputsObservable()
                .distinctUntilChanged()
                .collect { refreshGamePadBindingsScreen(it) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            getSaveSyncScreen()?.let { screen ->
                PendingOperationsMonitor(requireContext())
                    .anySaveOperationInProgress()
                    .safeCollect { syncInProgress ->
                        saveSyncPreferences.updatePreferences(screen, syncInProgress)
                    }
            }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceManager.preferenceDataStore =
            SharedPreferencesHelper.getSharedPreferencesDataStore(requireContext())
        setPreferencesFromResource(R.xml.tv_settings, rootKey)

        getBiosInfoPreferenceScreen()?.let {
            biosPreferences.addBiosPreferences(it)
        }

        getAdvancedSettingsPreferenceScreen()?.let {
        }

        // Lemuroid-S: the HD mode quality is used while HD mode is the chosen display filter.
        val hdQuality = findPreference<Preference>(getString(R.string.pref_key_hd_mode_quality))
        findPreference<androidx.preference.ListPreference>(getString(R.string.pref_key_shader_filter))?.let { filter ->
            hdQuality?.isEnabled = filter.value == SettingsManager.HD_FILTER
            filter.setOnPreferenceChangeListener { _, value ->
                hdQuality?.isEnabled = value == SettingsManager.HD_FILTER
                true
            }
        }

        // Lemuroid-S: newer settings.
        findPreference<PreferenceScreen>(getString(R.string.pref_key_open_core_settings))?.let { addCoreSettings(it) }
        findPreference<Preference>(getString(R.string.pref_key_tv_language))?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val updatesSupported = AppUpdater(requireContext().applicationContext).isSupported
        findPreference<Preference>(AppUpdater.PREF_FREQUENCY)?.isVisible = updatesSupported
        findPreference<Preference>(getString(R.string.pref_key_tv_check_updates))?.isVisible = updatesSupported

        getSaveSyncScreen()?.let {
            if (saveSyncManager.isSupported()) {
                saveSyncPreferences.addSaveSyncPreferences(it)
            }
            it.isVisible = saveSyncManager.isSupported()
        }
    }

    /** One category per core, with the settings it reported the last time one of its games ran. */
    private fun addCoreSettings(screen: PreferenceScreen) {
        val context = screen.context
        screen.removeAll()
        GameSystem.all()
            .flatMap { it.systemCoreConfigs }
            .filter { it.exposedSettings.isNotEmpty() || it.exposedAdvancedSettings.isNotEmpty() }
            .map { it.coreID }
            .distinct()
            .sortedBy { it.coreDisplayName.lowercase() }
            .forEach { coreID ->
                val systems =
                    GameSystem.all()
                        .filter { system -> system.systemCoreConfigs.any { it.coreID == coreID } }
                        .joinToString(", ") { getString(it.shortTitleResId) }
                val category = PreferenceCategory(context)
                category.title = "${coreID.coreDisplayName} ($systems)"
                category.isIconSpaceReserved = false
                screen.addPreference(category)

                val options = CoreOptionsCache.load(context, coreID)
                if (options.isEmpty()) {
                    category.addPreference(
                        Preference(context).apply {
                            summary = getString(R.string.core_settings_play_first)
                            isSelectable = false
                            isIconSpaceReserved = false
                        },
                    )
                } else {
                    options.forEach {
                        category.addPreference(CoreOptionsPreferenceHelper.buildPreference(context, it, coreID.coreName))
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        refreshSaveSyncScreen()
    }

    private fun getGamePadPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_gamepad_settings))
    }

    private fun getSaveSyncScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_save_sync_settings))
    }

    private fun getBiosInfoPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_display_bios_info))
    }

    private fun getAdvancedSettingsPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_advanced_settings))
    }

    private fun addGamePadBindingsScreen(
        gamePads: List<InputDevice>,
        enabledGamePads: List<InputDevice>,
    ) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                it.removeAll()
                gamePadPreferencesHelper.addGamePadsPreferencesToScreen(
                    it.context,
                    it,
                    gamePads,
                    enabledGamePads,
                )
            }
        }
    }

    private fun refreshGamePadBindingsScreen(enabledGamePads: List<InputDevice>) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                gamePadPreferencesHelper.refreshGamePadsPreferencesToScreen(it, enabledGamePads)
            }
        }
    }

    private fun refreshSaveSyncScreen() {
        getSaveSyncScreen()?.let {
            saveSyncPreferences.updatePreferences(it, false)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (saveSyncPreferences.onPreferenceTreeClick(activity, preference)) {
            return true
        }

        when (preference.key) {
            getString(R.string.pref_key_reset_gamepad_bindings) ->
                lifecycleScope.launch {
                    handleResetGamePadBindings()
                }
            getString(R.string.pref_key_reset_settings) -> handleResetSettings()
            getString(R.string.pref_key_tv_first_launch_setup) -> TVSetupActivity.start(requireContext())
            getString(R.string.pref_key_tv_check_updates) -> AppUpdateRequests.manualCheck.tryEmit(Unit)
            getString(R.string.pref_key_tv_language) ->
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.fromParts("package", requireContext().packageName, null),
                        ),
                    )
                }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private suspend fun handleResetGamePadBindings() {
        inputDeviceManager.resetAllBindings()
        refreshGamePadBindingsScreen(inputDeviceManager.getGamePadsObservable().first())
    }

    private fun handleResetSettings() {
        settingsInteractor.resetAllSettings()
        activity?.finish()
    }

    @dagger.Module
    class Module
}
