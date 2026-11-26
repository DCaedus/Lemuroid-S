package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import timber.log.Timber

/**
 * Runs "Import RetroArch Covers" in the background, like the ROMs scan and the saves import: with a
 * notification and the app's "working" animation, and it keeps going if the user leaves Settings.
 */
class CoversImportWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        val folder = inputData.getString(KEY_FOLDER)?.let { Uri.parse(it) } ?: return Result.failure()

        val notification = NotificationsManager(applicationContext).coversImportNotification()
        setForegroundAsync(createSyncForegroundInfo(NotificationsManager.COVERS_IMPORT_NOTIFICATION_ID, notification))

        val copied =
            runCatching { RetroArchCoversImporter(applicationContext).importFrom(folder) }
                .onFailure { Timber.e(it, "Covers import failed") }
                .getOrNull()

        val message =
            if (copied != null) {
                applicationContext.getString(R.string.import_covers_result, copied)
            } else {
                applicationContext.getString(R.string.import_covers_failed)
            }
        NotificationsManager(applicationContext).showResult(
            NotificationsManager.COVERS_IMPORT_RESULT_NOTIFICATION_ID,
            applicationContext.getString(
                if (copied != null) R.string.covers_import_result_title else R.string.covers_import_failed_title,
            ),
            message,
        )
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
        return Result.success()
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(CoversImportWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<CoversImportWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<CoversImportWork>()
    }

    companion object {
        val UNIQUE_WORK_ID: String = CoversImportWork::class.java.simpleName
        private const val KEY_FOLDER = "folder"

        fun start(
            context: Context,
            folder: Uri,
        ) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CoversImportWork>()
                    .setInputData(workDataOf(KEY_FOLDER to folder.toString()))
                    .build(),
            )
        }
    }
}
