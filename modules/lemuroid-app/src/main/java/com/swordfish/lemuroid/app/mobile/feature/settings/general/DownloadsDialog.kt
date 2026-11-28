package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoversDownloadWork
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase

/**
 * Lemuroid-S: "Download cores and covers". Cores and covers are normally fetched when needed; this
 * downloads them now (optionally only over Wi-Fi), for the consoles in the library.
 */
class CoreDownloads(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val coreUpdater: CoreUpdater,
) {
    /** A core in use, labelled "NES (FCEUmm)" after the consoles it runs. */
    data class Choice(val coreID: CoreID, val label: String, val status: CoreUpdater.CoreStatus)

    suspend fun choices(): List<Choice> {
        val systems =
            retrogradeDb.gameDao().selectSystems()
                .mapNotNull { runCatching { GameSystem.findById(it) }.getOrNull() }
        val byCore = systems.groupBy { it.systemCoreConfigs.first().coreID }
        val statuses = coreUpdater.coreStatus(appContext, byCore.keys.toList())
        return byCore
            .map { (coreID, coreSystems) ->
                val names = coreSystems.map { appContext.getString(it.shortTitleResId) }.sorted()
                val label = "${names.joinToString(", ")} (${coreID.coreDisplayName})"
                Choice(coreID, label, statuses[coreID] ?: CoreUpdater.CoreStatus(false, null))
            }
            .sortedBy { it.label.lowercase() }
    }

    fun start(
        cores: List<CoreID>,
        covers: Boolean,
        wifiOnly: Boolean,
    ) {
        if (cores.isNotEmpty()) LibraryIndexScheduler.scheduleCoreDownload(appContext, cores, wifiOnly)
        if (covers) CoversDownloadWork.start(appContext, wifiOnly)
    }
}

val LocalCoreDownloads = staticCompositionLocalOf<CoreDownloads?> { null }

@Composable
fun DownloadsDialog(
    onDismiss: () -> Unit,
    onStarted: () -> Unit = { },
) {
    val context = LocalContext.current
    val downloads = LocalCoreDownloads.current ?: return
    var choices by remember { mutableStateOf<List<CoreDownloads.Choice>?>(null) }
    val selectedCores = remember { mutableStateListOf<CoreID>() }
    var covers by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = runCatching { downloads.choices() }.getOrDefault(emptyList())
        selectedCores.addAll(loaded.filter { !it.status.installed }.map { it.coreID })
        choices = loaded
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.downloads_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    modifier = Modifier.padding(bottom = 8.dp),
                    text = stringResource(R.string.downloads_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CheckRow(stringResource(R.string.downloads_covers), null, covers, true) { covers = it }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val loaded = choices
                when {
                    loaded == null ->
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    loaded.isEmpty() ->
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = stringResource(R.string.downloads_no_cores),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    else -> {
                        val available = loaded.filter { !it.status.installed }.map { it.coreID }
                        CheckRow(
                            stringResource(R.string.downloads_all_cores),
                            null,
                            available.isNotEmpty() && selectedCores.containsAll(available),
                            available.isNotEmpty(),
                        ) { all ->
                            selectedCores.clear()
                            if (all) selectedCores.addAll(available)
                        }
                        loaded.forEach { choice ->
                            val size = choice.status.sizeBytes
                            val detail =
                                when {
                                    choice.status.installed -> stringResource(R.string.downloads_core_installed)
                                    size != null -> Formatter.formatShortFileSize(context, size)
                                    else -> null
                                }
                            CheckRow(choice.label, detail, choice.coreID in selectedCores, !choice.status.installed) { checked ->
                                if (checked) selectedCores.add(choice.coreID) else selectedCores.remove(choice.coreID)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                CheckRow(stringResource(R.string.downloads_wifi_only), null, wifiOnly, true) { wifiOnly = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = covers || selectedCores.isNotEmpty(),
                onClick = {
                    downloads.start(selectedCores.toList(), covers, wifiOnly)
                    Toast.makeText(context, R.string.downloads_started, Toast.LENGTH_LONG).show()
                    onStarted()
                    onDismiss()
                },
            ) { Text(text = stringResource(R.string.downloads_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CheckRow(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(modifier = Modifier.weight(1f), text = label, style = MaterialTheme.typography.bodyMedium)
        if (detail != null) {
            Text(text = detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
