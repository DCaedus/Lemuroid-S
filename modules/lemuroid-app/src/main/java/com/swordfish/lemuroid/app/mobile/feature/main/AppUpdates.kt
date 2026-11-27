package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.ext.feature.update.AppUpdater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Lemuroid-S: asks the update dialog to check GitHub now (Settings > Check for updates). */
object AppUpdateRequests {
    val manualCheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

private sealed interface UpdateState {
    data object Idle : UpdateState

    data class Available(val release: AppUpdater.Release) : UpdateState

    data class Downloading(val release: AppUpdater.Release, val progress: Float?) : UpdateState
}

/**
 * GitHub version only: checks for a new release when due (and when asked from Settings), offers it,
 * downloads it with progress and opens Android's installer. If the download fails, the release page
 * on GitHub opens instead.
 */
@Composable
fun AppUpdateDialogs() {
    val context = LocalContext.current
    val updater = remember { AppUpdater(context.applicationContext) }
    if (!updater.isSupported) return

    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    LaunchedEffect(Unit) {
        updater.checkIfDue()?.let { state = UpdateState.Available(it) }
    }
    // Only the screen in front answers "Check for updates now" (on TV both the home and the settings
    // screen show these dialogs).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AppUpdateRequests.manualCheck.collect {
            Toast.makeText(context, R.string.update_checking, Toast.LENGTH_SHORT).show()
                val release = updater.checkNow()
                if (release != null) {
                    state = UpdateState.Available(release)
                } else {
                    Toast.makeText(context, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    when (val current = state) {
        is UpdateState.Available ->
            AlertDialog(
                onDismissRequest = { state = UpdateState.Idle },
                title = { Text(stringResource(R.string.update_available_title, current.release.version)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (current.release.notes.isNotBlank()) {
                            Text(current.release.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val release = current.release
                            if (!updater.canInstallInApp || release.apkUrl == null) {
                                openReleasePage(context, updater, release)
                                state = UpdateState.Idle
                                return@TextButton
                            }
                            state = UpdateState.Downloading(release, null)
                            scope.launch {
                                val apk =
                                    updater.download(release) { progress ->
                                        state = UpdateState.Downloading(release, progress)
                                    }
                                state = UpdateState.Idle
                                val installed =
                                    apk != null &&
                                        runCatching { context.startActivity(updater.installIntent(apk)) }.isSuccess
                                if (!installed) {
                                    Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                                    openReleasePage(context, updater, release)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.update_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateState.Idle }) { Text(stringResource(R.string.update_later)) }
                },
            )
        is UpdateState.Downloading ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading, current.release.version)) },
                text = {
                    val progress = current.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
            )
        UpdateState.Idle -> Unit
    }
}

private fun openReleasePage(
    context: Context,
    updater: AppUpdater,
    release: AppUpdater.Release,
) {
    runCatching { context.startActivity(updater.releasePageIntent(release)) }
}
