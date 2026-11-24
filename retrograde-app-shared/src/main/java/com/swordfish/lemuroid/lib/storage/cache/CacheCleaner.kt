package com.swordfish.lemuroid.lib.storage.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Empties the app cache on request. Games are loaded straight from their folder, so there is no game cache. */
object CacheCleaner {
    suspend fun cleanAll(appContext: Context): Unit =
        withContext(Dispatchers.IO) {
            Timber.i("Running cache cleanup everything task")
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
}
