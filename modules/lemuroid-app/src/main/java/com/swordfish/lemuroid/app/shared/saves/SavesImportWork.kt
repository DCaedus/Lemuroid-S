package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import dagger.multibindings.ClassKey
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import timber.log.Timber
import javax.inject.Inject

/**
 * Runs "Import Saves from other Emulators" in the background, like the library scan, so the app
 * shows the same "working" animation and the import isn't interrupted by leaving the screen.
 */
class SavesImportWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var savesImporter: SavesImporter

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        val folder = inputData.getString(KEY_FOLDER)?.let { Uri.parse(it) } ?: return Result.failure()

        // Same kind of progress notification as the ROMs scan.
        val notification = NotificationsManager(applicationContext).savesImportNotification()
        setForegroundAsync(createSyncForegroundInfo(NotificationsManager.SAVES_IMPORT_NOTIFICATION_ID, notification))

        val result =
            runCatching { savesImporter.importFrom(folder) }
                .onFailure { Timber.e(it, "Saves import failed") }
                .getOrNull()

        showResult(result)
        return Result.success()
    }

    private fun showResult(result: SavesImporter.Result?) {
        val message =
            if (result != null) {
                applicationContext.getString(R.string.import_saves_result, result.saves, result.states, result.skipped)
            } else {
                applicationContext.getString(R.string.import_saves_failed)
            }
        NotificationsManager(applicationContext).showResult(
            NotificationsManager.SAVES_IMPORT_RESULT_NOTIFICATION_ID,
            applicationContext.getString(
                if (result != null) R.string.saves_import_result_title else R.string.saves_import_failed_title,
            ),
            message,
        )
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @ClassKey(SavesImportWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<*>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<SavesImportWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<SavesImportWork>()
    }

    companion object {
        val UNIQUE_WORK_ID: String = SavesImportWork::class.java.simpleName
        private const val KEY_FOLDER = "folder"

        fun start(
            context: Context,
            folder: Uri,
        ) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SavesImportWork>()
                    .setInputData(workDataOf(KEY_FOLDER to folder.toString()))
                    .build(),
            )
        }
    }
}
