package com.swordfish.lemuroid.app.shared.saves

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.saves.SavesFallbackMover
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import timber.log.Timber
import javax.inject.Inject

/**
 * At app start, moves saves and states left in app storage into the SAVES folder (see
 * [SavesFallbackMover]). Quick when there is nothing to move.
 */
class SavesMoveWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var savesFallbackMover: SavesFallbackMover

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        runCatching { savesFallbackMover.moveAll() }
            .onFailure { Timber.e(it, "Moving saves into the SAVES folder failed") }
        return Result.success()
    }

    companion object {
        private val UNIQUE_WORK_ID: String = SavesMoveWork::class.java.simpleName

        fun enqueue(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SavesMoveWork>().build(),
            )
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(SavesMoveWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<SavesMoveWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<SavesMoveWork>()
    }
}
