package com.swordfish.lemuroid.ext.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.core.content.FileProvider
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lemuroid-S updates for the GitHub version: checks the latest GitHub release (as often as chosen in
 * Settings), downloads its APK and hands it to Android's installer. Google Play updates its own apps,
 * so the Play version has a stand-in that does nothing.
 */
class AppUpdater(context: Context) {
    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    )

    private val appContext = context.applicationContext
    private val preferences = SharedPreferencesHelper.getSharedPreferences(appContext)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    val isSupported: Boolean = true

    /** Checks GitHub if the chosen interval has passed since the last check. */
    suspend fun checkIfDue(): Release? {
        val intervalDays = frequencyDays() ?: return null
        val last = preferences.getLong(PREF_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < TimeUnit.DAYS.toMillis(intervalDays)) return null
        return checkNow()
    }

    /** Asks GitHub for the latest release; returns it if it's newer than this app. */
    suspend fun checkNow(): Release? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(LATEST_RELEASE_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Lemuroid-S")
                        .build()
                val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
                preferences.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
                body?.let { parseRelease(JSONObject(it)) }?.takeIf { isNewer(it.version, currentVersion()) }
            }.onFailure { Timber.w(it, "Update check failed") }.getOrNull()
        }

    /** Downloads the release's APK, reporting progress from 0 to 1. Null if it fails. */
    suspend fun download(
        release: Release,
        onProgress: (Float) -> Unit,
    ): File? =
        withContext(Dispatchers.IO) {
            val url = release.apkUrl ?: return@withContext null
            runCatching {
                val folder = File(appContext.cacheDir, UPDATE_FOLDER).apply { deleteRecursively(); mkdirs() }
                val target = File(folder, "lemuroid-s-${release.version}.apk")
                client.newCall(Request.Builder().url(url).header("User-Agent", "Lemuroid-S").build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = checkNotNull(response.body) { "Empty response" }
                    val total = body.contentLength().takeIf { it > 0 }
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                done += count
                                if (total != null) onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
                target
            }.onFailure { Timber.w(it, "Update download failed") }.getOrNull()
        }

    /** Opens Android's installer for the downloaded APK (it asks to allow installs from Lemuroid-S if needed). */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The release's page on GitHub, used when the download or install doesn't work. */
    fun releasePageIntent(release: Release): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Test builds are a separate app (".debug"), so they only link to GitHub. */
    val canInstallInApp: Boolean
        get() = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

    private fun frequencyDays(): Long? =
        when (preferences.getString(PREF_FREQUENCY, DEFAULT_FREQUENCY)) {
            "daily" -> 1
            "weekly" -> 7
            "monthly" -> 30
            else -> null
        }

    private fun currentVersion(): String =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()

    private fun parseRelease(json: JSONObject): Release {
        val assets = json.optJSONArray("assets")
        val apkUrl =
            (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
        return Release(
            version = json.optString("tag_name").removePrefix("v").removePrefix("V"),
            notes = json.optString("body").take(MAX_NOTES_LENGTH),
            apkUrl = apkUrl,
            pageUrl = json.optString("html_url", RELEASES_PAGE_URL),
        )
    }

    companion object {
        const val PREF_FREQUENCY = "update_check_frequency"
        const val DEFAULT_FREQUENCY = "weekly"
        private const val PREF_LAST_CHECK = "update_last_check"
        private const val UPDATE_FOLDER = "updates"
        private const val MAX_NOTES_LENGTH = 800
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/DCaedus/Lemuroid-S/releases/latest"
        private const val RELEASES_PAGE_URL = "https://github.com/DCaedus/Lemuroid-S/releases"

        /** True if [candidate] ("1.3", "1.2.1") is a higher version than [current] ("1.2", "1.2-DEBUG"). */
        fun isNewer(
            candidate: String,
            current: String,
        ): Boolean {
            fun parts(version: String) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (diff != 0) return diff > 0
            }
            return false
        }
    }
}
