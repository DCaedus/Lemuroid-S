package com.swordfish.lemuroid.lib.core

/**
 * Where the GitHub build of Lemuroid-S downloads its on-demand cores: the "cores" branch of the
 * Lemuroid-S repository, one folder per tested snapshot of libretro's nightly Android builds
 * (buildbot.libretro.com), zipped, one file per ABI and core: "<abi>-lib<core>_libretro_android.so.zip".
 * Change [VERSION] to move every user to a new snapshot (old cores are deleted on the next download).
 */
object CoresRelease {
    const val VERSION = "2026.11.24"

    private const val BASE_URL = "https://github.com/DCaedus/Lemuroid-S/raw/cores/$VERSION/"

    fun coreUrl(
        abi: String,
        libFileName: String,
    ): String = "$BASE_URL$abi-$libFileName.zip"

    /** Support files for PPSSPP (fonts, shaders...), matching the PPSSPP core of this release. */
    const val PPSSPP_ASSETS_URL = "${BASE_URL}ppsspp-assets.zip"
}
