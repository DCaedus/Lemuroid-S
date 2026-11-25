package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.general.rememberMainFolderPicker
import com.swordfish.lemuroid.app.shared.settings.RomsFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lemuroid-S keeps saves, ROMs and covers in the Lemuroid-S folder. Each time the app opens or comes
 * back to the foreground it checks that Android still lets it in; if not, this screen covers the
 * whole app until the user chooses the folder again (nothing can be played meanwhile).
 */
@Composable
fun MainFolderAccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<MainFolder.Access?>(null) }

    val recheck: () -> Unit = {
        scope.launch {
            access = withContext(Dispatchers.IO) { MainFolder(context).checkAccess() }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) recheck()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val picker =
        rememberMainFolderPicker(explainFirst = false) {
            scope.launch {
                // A moved or recreated folder has new addresses: point the ROMs folder at it again.
                if (RomsFolder.isMainLocation(context)) RomsFolder.useMainFolder(context)
                recheck()
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (access == MainFolder.Access.LOST) {
            BackHandler { (context as? Activity)?.finish() }
            FolderLostScreen(onChooseFolder = picker)
        }
    }
}

@Composable
private fun FolderLostScreen(onChooseFolder: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.main_folder_lost_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.main_folder_lost_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onChooseFolder) {
                Text(text = stringResource(R.string.main_folder_lost_action))
            }
        }
    }
}
