package com.swordfish.lemuroid.lib.core

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Lemuroid-S: the app's own native libraries (cores shipped in the app or in Play modules).
 *
 * The app keeps them uncompressed inside its APKs instead of unpacking a second copy
 * (android:extractNativeLibs="false"), so they're loaded straight from the APK: dlopen accepts
 * "<apk>!/lib/<abi>/<library>" for libraries stored that way. Libraries that were unpacked anyway
 * (older installs, cores downloaded into the app's files) are found too.
 */
object NativeLibraries {
    /** A path dlopen can load [fileName] from, or null if the app doesn't have it. */
    fun find(
        context: Context,
        fileName: String,
    ): String? {
        val unpacked = File(context.applicationInfo.nativeLibraryDir, fileName)
        if (unpacked.exists()) return unpacked.absolutePath

        for (apk in apks(context)) {
            val entry = entries(apk).firstOrNull { it.endsWith("/$fileName") } ?: continue
            return "$apk!/$entry"
        }

        return context.filesDir.walkBottomUp().firstOrNull { it.name == fileName }?.absolutePath
    }

    /** File names of the libraries shipped in the app's APKs (and any unpacked copies). */
    fun names(context: Context): Set<String> {
        val unpacked = File(context.applicationInfo.nativeLibraryDir).list()?.toSet() ?: emptySet()
        val inApks = apks(context).flatMap { entries(it) }.map { it.substringAfterLast('/') }
        return unpacked + inApks
    }

    /** The processor type the installed app's libraries are for (e.g. "arm64-v8a"), if it has any. */
    fun abi(context: Context): String? =
        Build.SUPPORTED_ABIS.firstOrNull { abi ->
            apks(context).any { apk -> entries(apk).any { it.startsWith("lib/$abi/") } }
        }

    /**
     * Library entries ("lib/<abi>/<name>") stored uncompressed in [apk], for the first processor type
     * of this device the APK has libraries for.
     */
    private fun entries(apk: String): List<String> =
        cache.getOrPut(apk) {
            runCatching {
                ZipFile(apk).use { zip ->
                    val libraries =
                        zip.entries().asSequence()
                            .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") && it.method == ZipEntry.STORED }
                            .map { it.name }
                            .toList()
                    val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> libraries.any { it.startsWith("lib/$abi/") } }
                    libraries.filter { abi != null && it.startsWith("lib/$abi/") }
                }
            }.getOrDefault(emptyList())
        }

    /** The base APK and its splits, read fresh so Play modules installed meanwhile are included. */
    private fun apks(context: Context): List<String> {
        val info =
            runCatching { context.packageManager.getApplicationInfo(context.packageName, 0) }
                .getOrDefault(context.applicationInfo)
        return listOfNotNull(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
}
