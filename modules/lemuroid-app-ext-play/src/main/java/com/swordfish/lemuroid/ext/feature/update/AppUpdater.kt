package com.swordfish.lemuroid.ext.feature.update

import android.content.Context
import android.content.Intent
import java.io.File

/** Google Play updates the app itself: the GitHub updater (free version) is not part of this build. */
@Suppress("UNUSED_PARAMETER")
class AppUpdater(context: Context) {
    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    )

    val isSupported: Boolean = false

    val canInstallInApp: Boolean = false

    suspend fun checkIfDue(): Release? = null

    suspend fun checkNow(): Release? = null

    suspend fun download(
        release: Release,
        onProgress: (Float) -> Unit,
    ): File? = null

    fun installIntent(apk: File): Intent = Intent()

    fun releasePageIntent(release: Release): Intent = Intent()

    companion object {
        const val PREF_FREQUENCY = "update_check_frequency"
        const val DEFAULT_FREQUENCY = "weekly"
    }
}
