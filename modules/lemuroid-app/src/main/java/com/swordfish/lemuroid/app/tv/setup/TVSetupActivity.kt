package com.swordfish.lemuroid.app.tv.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.FirstLaunchSetup
import com.swordfish.lemuroid.app.mobile.feature.main.FirstLaunchSetupScreen
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerLauncher
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.MainFolder
import java.io.File

/**
 * Lemuroid-S: setup on Android TV / Google TV. TVs with a folder picker get the same setup as phones
 * (Lemuroid-S folder, ROMs folder...). TVs without one use the app's own folder: games are copied
 * into it from a computer. TVs on Android 10 or older can still browse for a ROMs folder.
 */
class TVSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appFolderOnly = intent.getBooleanExtra(EXTRA_APP_FOLDER_INFO, false)
        setContent {
            AppTheme {
                if (!appFolderOnly && TVHelper.isSAFSupported(this)) {
                    FirstLaunchSetupScreen(onDone = { done() })
                } else {
                    AppFolderScreen(onDone = { done() })
                }
            }
        }
    }

    private fun done() {
        FirstLaunchSetup.finish(applicationContext)
        finish()
    }

    @Composable
    private fun AppFolderScreen(onDone: () -> Unit) {
        val romsFolder = remember { prepareAppRomsFolder(this) }
        val canBrowse = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.tv_setup_title), style = MaterialTheme.typography.headlineSmall)
                Text(text = stringResource(R.string.tv_setup_app_folder_message), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = romsFolder.absolutePath + "/<" + stringResource(R.string.tv_setup_console_folder) + ">",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(text = stringResource(R.string.tv_setup_app_folder_how), style = MaterialTheme.typography.bodyMedium)
                if (canBrowse) {
                    Text(text = stringResource(R.string.tv_setup_browse_message), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        modifier = Modifier.focusRequester(focus),
                        onClick = {
                            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                            onDone()
                        },
                    ) {
                        Text(text = stringResource(R.string.tv_setup_scan))
                    }
                    if (canBrowse) {
                        OutlinedButton(onClick = {
                            TVFolderPickerLauncher.pickFolder(this@TVSetupActivity)
                            onDone()
                        }) {
                            Text(text = stringResource(R.string.tv_setup_browse))
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_APP_FOLDER_INFO = "EXTRA_APP_FOLDER_INFO"

        /** First launch: the setup that fits this TV. */
        fun start(context: Context) {
            context.startActivity(Intent(context, TVSetupActivity::class.java))
        }

        /** "Choose ROMs folder" on a TV without a folder picker: where to copy the games. */
        fun showAppFolder(context: Context) {
            context.startActivity(
                Intent(context, TVSetupActivity::class.java).putExtra(EXTRA_APP_FOLDER_INFO, true),
            )
        }

        /** The app's own ROMs folder (scanned when no other ROMs folder is set), with a folder per console. */
        fun prepareAppRomsFolder(context: Context): File {
            val roms = DirectoriesManager(context.applicationContext).getInternalRomsDirectory()
            MainFolder.SYSTEM_FOLDERS.values.forEach { File(roms, it).mkdirs() }
            return roms
        }
    }
}
