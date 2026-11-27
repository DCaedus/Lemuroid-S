package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ): Boolean {
        // Saves live in the Lemuroid-S folder: no playing while Android doesn't let us in.
        if (MainFolder(activity.applicationContext).checkAccess() == MainFolder.Access.LOST) {
            activity.displayToast(R.string.main_folder_lost_title)
            return false
        }

        val previousGameRunning = GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)

        GlobalScope.launch {
            // Lemuroid-S: a game that was just closed may still be writing its saves; wait for it.
            if (previousGameRunning && !waitForPreviousGame(activity)) {
                withContext(Dispatchers.Main) { activity.displayToast(R.string.game_process_another_game_running) }
                return@launch
            }
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
        }

        return true
    }

    private suspend fun waitForPreviousGame(activity: Activity): Boolean {
        withContext(Dispatchers.Main) { activity.displayToast(R.string.game_saving_previous) }
        return withTimeoutOrNull(PREVIOUS_GAME_TIMEOUT_MS) {
            while (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) delay(POLL_MS)
            true
        } ?: false
    }

    companion object {
        private const val PREVIOUS_GAME_TIMEOUT_MS = 20_000L
        private const val POLL_MS = 250L
    }
}
