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

    /**
     * Download size in bytes of each core of this release, as published ("<abi>-<library>"), so the
     * app can show it without asking the server (Google Play doesn't say before installing).
     * Regenerate when VERSION changes.
     */
    fun downloadSize(
        abi: String,
        libFileName: String,
    ): Long? = DOWNLOAD_SIZES["$abi-$libFileName"]

    private val DOWNLOAD_SIZES =
        mapOf(
            "arm64-v8a-liba5200_libretro_android.so" to 97164L,
            "arm64-v8a-libazahar_libretro_android.so" to 11995344L,
            "arm64-v8a-libdosbox_pure_libretro_android.so" to 1565047L,
            "arm64-v8a-libfbneo_libretro_android.so" to 17291416L,
            "arm64-v8a-libflycast_libretro_android.so" to 4910844L,
            "arm64-v8a-libfreeintv_libretro_android.so" to 499832L,
            "arm64-v8a-libgearcoleco_libretro_android.so" to 309102L,
            "arm64-v8a-libgenesis_plus_gx_libretro_android.so" to 1134047L,
            "arm64-v8a-libhandy_libretro_android.so" to 141020L,
            "arm64-v8a-libmame2003_plus_libretro_android.so" to 9899921L,
            "arm64-v8a-libmednafen_ngp_libretro_android.so" to 152520L,
            "arm64-v8a-libmednafen_pce_fast_libretro_android.so" to 374270L,
            "arm64-v8a-libmednafen_vb_libretro_android.so" to 129196L,
            "arm64-v8a-libmednafen_wswan_libretro_android.so" to 97945L,
            "arm64-v8a-libmelondsds_libretro_android.so" to 2003724L,
            "arm64-v8a-libmupen64plus_next_gles3_libretro_android.so" to 2201469L,
            "arm64-v8a-libneocd_libretro_android.so" to 535179L,
            "arm64-v8a-libpicodrive_libretro_android.so" to 604891L,
            "arm64-v8a-libpokemini_libretro_android.so" to 85574L,
            "arm64-v8a-libppsspp_libretro_android.so" to 8312085L,
            "arm64-v8a-libprosystem_libretro_android.so" to 47540L,
            "arm64-v8a-libstella_libretro_android.so" to 1479249L,
            "arm64-v8a-libvecx_libretro_android.so" to 39653L,
            "armeabi-v7a-liba5200_libretro_android.so" to 74176L,
            "armeabi-v7a-libdosbox_pure_libretro_android.so" to 1531199L,
            "armeabi-v7a-libfbneo_libretro_android.so" to 16006965L,
            "armeabi-v7a-libflycast_libretro_android.so" to 4946946L,
            "armeabi-v7a-libfreeintv_libretro_android.so" to 463155L,
            "armeabi-v7a-libgearcoleco_libretro_android.so" to 258983L,
            "armeabi-v7a-libgenesis_plus_gx_libretro_android.so" to 1078109L,
            "armeabi-v7a-libhandy_libretro_android.so" to 113799L,
            "armeabi-v7a-libmame2003_plus_libretro_android.so" to 7937363L,
            "armeabi-v7a-libmednafen_ngp_libretro_android.so" to 124934L,
            "armeabi-v7a-libmednafen_pce_fast_libretro_android.so" to 301157L,
            "armeabi-v7a-libmednafen_vb_libretro_android.so" to 101149L,
            "armeabi-v7a-libmednafen_wswan_libretro_android.so" to 97335L,
            "armeabi-v7a-libmelondsds_libretro_android.so" to 1689542L,
            "armeabi-v7a-libmupen64plus_next_gles3_libretro_android.so" to 2307737L,
            "armeabi-v7a-libneocd_libretro_android.so" to 506292L,
            "armeabi-v7a-libpicodrive_libretro_android.so" to 631532L,
            "armeabi-v7a-libpokemini_libretro_android.so" to 67547L,
            "armeabi-v7a-libppsspp_libretro_android.so" to 7750299L,
            "armeabi-v7a-libprosystem_libretro_android.so" to 36344L,
            "armeabi-v7a-libstella_libretro_android.so" to 1135517L,
            "armeabi-v7a-libvecx_libretro_android.so" to 33125L,
            "x86-liba5200_libretro_android.so" to 95761L,
            "x86-libdosbox_pure_libretro_android.so" to 1577487L,
            "x86-libfbneo_libretro_android.so" to 15688522L,
            "x86-libflycast_libretro_android.so" to 4996109L,
            "x86-libfreeintv_libretro_android.so" to 500546L,
            "x86-libgearcoleco_libretro_android.so" to 324793L,
            "x86-libgenesis_plus_gx_libretro_android.so" to 1056215L,
            "x86-libhandy_libretro_android.so" to 141230L,
            "x86-libmame2003_plus_libretro_android.so" to 9424754L,
            "x86-libmednafen_ngp_libretro_android.so" to 152948L,
            "x86-libmednafen_pce_fast_libretro_android.so" to 371274L,
            "x86-libmednafen_vb_libretro_android.so" to 130126L,
            "x86-libmednafen_wswan_libretro_android.so" to 94325L,
            "x86-libmupen64plus_next_gles3_libretro_android.so" to 1344427L,
            "x86-libneocd_libretro_android.so" to 573938L,
            "x86-libpicodrive_libretro_android.so" to 579621L,
            "x86-libpokemini_libretro_android.so" to 89923L,
            "x86-libppsspp_libretro_android.so" to 8872822L,
            "x86-libprosystem_libretro_android.so" to 47926L,
            "x86-libstella_libretro_android.so" to 1480659L,
            "x86-libvecx_libretro_android.so" to 38314L,
            "x86_64-liba5200_libretro_android.so" to 97987L,
            "x86_64-libdosbox_pure_libretro_android.so" to 1637239L,
            "x86_64-libfbneo_libretro_android.so" to 18386011L,
            "x86_64-libflycast_libretro_android.so" to 5067035L,
            "x86_64-libfreeintv_libretro_android.so" to 503631L,
            "x86_64-libgearcoleco_libretro_android.so" to 319931L,
            "x86_64-libgenesis_plus_gx_libretro_android.so" to 1167084L,
            "x86_64-libhandy_libretro_android.so" to 142939L,
            "x86_64-libmame2003_plus_libretro_android.so" to 10383307L,
            "x86_64-libmednafen_ngp_libretro_android.so" to 157727L,
            "x86_64-libmednafen_pce_fast_libretro_android.so" to 408044L,
            "x86_64-libmednafen_vb_libretro_android.so" to 129485L,
            "x86_64-libmednafen_wswan_libretro_android.so" to 107193L,
            "x86_64-libmelondsds_libretro_android.so" to 2057694L,
            "x86_64-libmupen64plus_next_gles3_libretro_android.so" to 1324849L,
            "x86_64-libneocd_libretro_android.so" to 598269L,
            "x86_64-libpicodrive_libretro_android.so" to 620568L,
            "x86_64-libpokemini_libretro_android.so" to 86999L,
            "x86_64-libppsspp_libretro_android.so" to 8763108L,
            "x86_64-libprosystem_libretro_android.so" to 48370L,
            "x86_64-libstella_libretro_android.so" to 1509461L,
            "x86_64-libvecx_libretro_android.so" to 41081L,
        )

    /** Support files for PPSSPP (fonts, shaders...), matching the PPSSPP core of this release. */
    const val PPSSPP_ASSETS_URL = "${BASE_URL}ppsspp-assets.zip"
}
