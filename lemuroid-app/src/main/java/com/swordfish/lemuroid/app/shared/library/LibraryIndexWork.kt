package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.shared.settings.RomsFolder
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class LibraryIndexWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var lemuroidLibrary: LemuroidLibrary

    @Inject
    lateinit var retrogradeDatabase: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val notificationsManager = NotificationsManager(applicationContext)

        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                notificationsManager.libraryIndexingNotification(),
            )

        setForegroundAsync(foregroundInfo)

        val gamesBefore = runCatching { retrogradeDatabase.gameDao().count() }.getOrDefault(0)
        val result =
            withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    RomsFolder.createMissingSystemFolders(applicationContext)
                    lemuroidLibrary.indexLibrary()
                }
            }
        if (result.isSuccess) {
            val gamesAfter = runCatching { retrogradeDatabase.gameDao().count() }.getOrDefault(0)
            notificationsManager.showResult(
                NotificationsManager.LIBRARY_RESULT_NOTIFICATION_ID,
                applicationContext.getString(R.string.library_result_title),
                applicationContext.getString(
                    R.string.library_result_message,
                    gamesAfter,
                    maxOf(gamesAfter - gamesBefore, 0),
                ),
            )
        }

        result.exceptionOrNull()?.let {
            Timber.e("Library indexing work terminated with an exception:", it)
        }

        LibraryIndexScheduler.scheduleCoreUpdate(applicationContext)

        return Result.success()
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(LibraryIndexWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<LibraryIndexWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<LibraryIndexWork>()
    }
}
