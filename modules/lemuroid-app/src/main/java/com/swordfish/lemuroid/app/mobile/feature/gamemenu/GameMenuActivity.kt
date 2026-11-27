@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import com.swordfish.lemuroid.app.shared.game.GameSystemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.lemuroid.app.mobile.feature.main.SCREEN_FADE_MS
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import java.security.InvalidParameterException
import javax.inject.Inject

class GameMenuActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    data class GameMenuRequest(
        val coreOptions: List<LemuroidCoreOption>,
        val advancedCoreOptions: List<LemuroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val fastForwardEnabled: Boolean,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialHdQuality =
            savedInstanceState?.takeIf { it.containsKey(STATE_INITIAL_HD_QUALITY) }
                ?.getInt(STATE_INITIAL_HD_QUALITY)
                ?: hdQuality()

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        GameSystemBars.showAll(this)

        val extras = intent.extras

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                fastForwardEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination

            val currentRoute =
                currentDestination?.route
                    ?.let { GameMenuRoute.findByRoute(it) }
                    ?: GameMenuRoute.HOME

            SideMenu {
                TopAppBar(
                    title = { Text(stringResource(currentRoute.titleId)) },
                    windowInsets = WindowInsets(0.dp),
                    navigationIcon = {
                        AnimatedContent(targetState = currentRoute.canGoBack(), label = "Back") { canGoBack ->
                            if (canGoBack) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.back),
                                    )
                                }
                            } else {
                                IconButton(onClick = { onResult { } }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        stringResource(R.string.close),
                                    )
                                }
                            }
                        }
                    },
                )
                Divider(modifier = Modifier.fillMaxWidth())
                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    navController = navController,
                    startDestination = GameMenuRoute.HOME.route,
                    enterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
                    exitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
                    popEnterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
                    popExitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
                ) {
                    composable(GameMenuRoute.HOME) {
                        GameMenuHomeScreen(navController, gameMenuRequest, ::onResult)
                    }
                    composable(GameMenuRoute.SAVE) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        false,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.LOAD) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        true,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.OPTIONS) {
                        GameMenuCoreOptionsScreen(
                            viewModel(
                                factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                            ),
                            gameMenuRequest,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SideMenu(content: @Composable () -> Unit) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val panelWidth =
                remember(maxWidth) {
                    minOf(maxWidth * 0.8f, 400f.dp)
                }

            // Lemuroid-S: the system bars show while the menu is open; the panel starts below the status bar.
            Surface(
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .fillMaxHeight()
                        .width(panelWidth)
                        .clip(MaterialTheme.shapes.large),
            ) {
                // Lemuroid-S: keep the menu clear of the status bar, navigation bar and display cutout.
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
                                    .union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
                            ),
                ) {
                    content()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) GameSystemBars.showAll(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        GameSystemBars.showAll(this)
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        if (hdQualityChanged()) resultIntent.putExtra(GameMenuContract.RESULT_SHADER_CHANGED, true)
        setResult(RESULT_OK, resultIntent)
        resultSet = true
        finish()
    }

    // Lemuroid-S: the HD mode quality slider changes the setting without closing the menu, so the
    // game is told to reload its shader whenever the menu closes with a different quality.
    private var initialHdQuality: Int? = null
    private var resultSet = false

    private fun hdQuality() =
        SharedPreferencesHelper.getSharedPreferences(this)
            .getInt(getString(R.string.pref_key_hd_mode_quality), SettingsManager.DEFAULT_HD_MODE_QUALITY)

    private fun hdQualityChanged() = initialHdQuality != null && hdQuality() != initialHdQuality

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        initialHdQuality?.let { outState.putInt(STATE_INITIAL_HD_QUALITY, it) }
    }

    override fun finish() {
        if (!resultSet && hdQualityChanged()) {
            setResult(RESULT_OK, Intent().putExtra(GameMenuContract.RESULT_SHADER_CHANGED, true))
        }
        super.finish()
    }

    @dagger.Module
    abstract class Module

    private companion object {
        const val STATE_INITIAL_HD_QUALITY = "initial_hd_quality"
    }
}
