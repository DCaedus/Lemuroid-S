package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import timber.log.Timber
import javax.inject.Inject

/**
 * Lemuroid-S: downloads the artwork of every game in the library that doesn't have it yet (from
 * "Download cores and covers"), instead of waiting for each game to show up on screen.
 */
class CoversDownloadWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var retrogradeDatabase: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val notifications = NotificationsManager(applicationContext)
        setForegroundAsync(
            createSyncForegroundInfo(
                NotificationsManager.COVERS_DOWNLOAD_NOTIFICATION_ID,
                notifications.coversDownloadNotification(),
            ),
        )

        val coverStore = CoverStore.get(applicationContext)
        var downloaded = 0
        runCatching {
            for (game in retrogradeDatabase.gameDao().selectAll()) {
                if (isStopped) break
                if (game.customCoverUri != null) continue
                val target = CoverUrls.target(applicationContext, game) ?: continue
                if (coverStore.resolve(target)?.downloaded == true) downloaded++
            }
        }.onFailure { Timber.e(it, "Covers download failed") }

        // Shown game artwork looks again, now that it's on the device.
        coverStore.forgetMainFolderListings()
        notifications.showResult(
            NotificationsManager.COVERS_DOWNLOAD_RESULT_NOTIFICATION_ID,
            applicationContext.getString(R.string.covers_download_result_title),
            applicationContext.getString(R.string.covers_download_result, downloaded),
        )
        return Result.success()
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @ClassKey(CoversDownloadWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<*>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<CoversDownloadWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<CoversDownloadWork>()
    }

    companion object {
        val UNIQUE_WORK_ID: String = CoversDownloadWork::class.java.simpleName

        fun start(
            context: Context,
            wifiOnly: Boolean,
        ) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CoversDownloadWork>()
                    .setConstraints(LibraryIndexScheduler.networkConstraints(wifiOnly))
                    .build(),
            )
        }
    }
}
