package com.swordfish.lemuroid.app.shared.game

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.view.Gravity
import android.text.format.Formatter
import android.app.AlertDialog
import timber.log.Timber
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.core.CoreUpdater
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
    private val coreUpdater: CoreUpdater,
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
            if (!ensureCoreDownloaded(activity, system, coreConfig.coreID)) return@launch
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
        }

        return true
    }

    /**
     * Lemuroid-S: automatic core downloads may be waiting for Wi-Fi. When the game's core isn't on
     * the device yet, asks whether to download it now (on any connection). Returns false when the
     * game shouldn't start: the user said no, or the download failed.
     */
    private suspend fun ensureCoreDownloaded(
        activity: Activity,
        system: GameSystem,
        coreID: CoreID,
    ): Boolean {
        val context = activity.applicationContext
        val status = runCatching { coreUpdater.coreStatus(context, listOf(coreID))[coreID] }.getOrNull()
        if (status == null || status.installed) return true

        val confirmed = withContext(Dispatchers.Main) { askToDownloadCore(activity, system, coreID, status.sizeBytes) }
        if (!confirmed) return false

        val progress = withContext(Dispatchers.Main) { showCoreDownloadProgress(activity, coreID) }
        try {
            runCatching { coreUpdater.downloadCores(context, listOf(coreID)) }
                .onFailure { Timber.e(it, "Could not download core $coreID") }
        } finally {
            withContext(Dispatchers.Main) { runCatching { progress?.dismiss() } }
        }

        val installed = runCatching { coreUpdater.coreStatus(context, listOf(coreID))[coreID]?.installed }.getOrNull()
        if (installed == false) {
            withContext(Dispatchers.Main) { activity.displayToast(R.string.core_download_failed) }
            return false
        }
        return true
    }

    private suspend fun askToDownloadCore(
        activity: Activity,
        system: GameSystem,
        coreID: CoreID,
        sizeBytes: Long?,
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (activity.isFinishing || activity.isDestroyed) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val systemName = activity.getString(system.titleResId)
            val message =
                if (sizeBytes != null) {
                    activity.getString(
                        R.string.core_download_message,
                        systemName,
                        coreID.coreDisplayName,
                        Formatter.formatShortFileSize(activity, sizeBytes),
                    )
                } else {
                    activity.getString(R.string.core_download_message_no_size, systemName, coreID.coreDisplayName)
                }
            val answer = { value: Boolean -> if (continuation.isActive) continuation.resume(value) }
            val dialog =
                AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.core_download_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.downloads_action) { _, _ -> answer(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> answer(false) }
                    .setOnCancelListener { answer(false) }
                    .show()
            continuation.invokeOnCancellation { runCatching { dialog.dismiss() } }
        }

    private fun showCoreDownloadProgress(
        activity: Activity,
        coreID: CoreID,
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(ProgressBar(activity))
                addView(
                    TextView(activity).apply {
                        text = activity.getString(R.string.core_downloading, coreID.coreDisplayName)
                        setPadding(padding, 0, 0, 0)
                    },
                )
            }
        return AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(content)
            .setCancelable(false)
            .show()
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
