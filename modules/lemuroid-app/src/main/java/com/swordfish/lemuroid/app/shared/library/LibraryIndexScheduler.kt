package com.swordfish.lemuroid.app.shared.library

import com.swordfish.lemuroid.lib.library.CoreID
import androidx.work.workDataOf
import androidx.work.NetworkType
import androidx.work.Constraints
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object LibraryIndexScheduler {
    val CORE_UPDATE_WORK_ID: String = CoreUpdateWork::class.java.simpleName
    val LIBRARY_INDEX_WORK_ID: String = LibraryIndexWork::class.java.simpleName

    fun scheduleLibrarySync(applicationContext: Context) {
        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                LIBRARY_INDEX_WORK_ID,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<LibraryIndexWork>().build(),
            )
            .enqueue()
    }

    fun scheduleCoreUpdate(applicationContext: Context) {
        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                CORE_UPDATE_WORK_ID,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                coreWorkRequest(applicationContext, null, DownloadSettings.wifiOnly(applicationContext)),
            )
            .enqueue()
    }

    /** Lemuroid-S: downloads [cores] now, or once on Wi-Fi when [wifiOnly]. */
    fun scheduleCoreDownload(
        applicationContext: Context,
        cores: List<CoreID>,
        wifiOnly: Boolean,
    ) {
        WorkManager.getInstance(applicationContext)
            .beginUniqueWork(
                CORE_UPDATE_WORK_ID,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                coreWorkRequest(applicationContext, cores, wifiOnly),
            )
            .enqueue()
    }

    private fun coreWorkRequest(
        applicationContext: Context,
        cores: List<CoreID>?,
        wifiOnly: Boolean,
    ): OneTimeWorkRequest {
        val builder =
            OneTimeWorkRequestBuilder<CoreUpdateWork>()
                .setConstraints(networkConstraints(wifiOnly))
        if (cores != null) builder.setInputData(workDataOf(CoreUpdateWork.KEY_CORES to cores.map { it.name }.toTypedArray()))
        if (wifiOnly) builder.addTag(DownloadSettings.WAITS_FOR_WIFI_TAG)
        return builder.build()
    }

    fun networkConstraints(wifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

    fun cancelLibrarySync(applicationContext: Context) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(LIBRARY_INDEX_WORK_ID)
    }

    fun cancelCoreUpdate(applicationContext: Context) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(CORE_UPDATE_WORK_ID)
    }
}
