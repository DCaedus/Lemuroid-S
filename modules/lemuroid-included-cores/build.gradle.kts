// Lemuroid-S: emulator cores shipped inside the app, so most systems work without a download:
// - in git (src/main/jniLibs): FCEUmm (NES), Snes9x (SNES), Gambatte (GB/GBC), mGBA (GBA),
//   PCSX ReARMed (PS1), libretro buildbot nightlies (buildbot.libretro.com/nightly/android) from 2026-11-24;
// - fetched at build time from the cores release like the Play modules (buildSrc/PlayCores.kt): see
//   FETCHED_CORES below.
// The others are downloaded on demand (Google Play in the
// Play build, GitHub otherwise).

plugins {
    id("com.android.library")
}

/**
 * Cores also shipped in the app: Sega 8/16-bit, Nintendo DS and 64, and the tiny handheld/retro ones.
 * The rest (arcade, the less common consoles, DOS, 3DS, PSP, Dreamcast) are downloaded when needed,
 * keeping the installed app small for phones with little storage.
 */
val FETCHED_CORES =
    listOf(
        "genesis_plus_gx",
        "melondsds",
        "mupen64plus_next_gles3",
        "mednafen_ngp",
        "handy",
        "mednafen_vb",
        "mednafen_wswan",
        "a5200",
        "pokemini",
        "prosystem",
        "vecx",
    )

android {
    namespace = "com.swordfish.lemuroid.includedcores"

    val coresDir = layout.buildDirectory.dir("includedCores").get().asFile
    FETCHED_CORES.forEach { core ->
        val dir = File(coresDir, core)
        PlayCores.registerFetch(project, core, "fetchIncludedCore_$core", dir, placeholders = false)
        sourceSets["main"].jniLibs.srcDir(dir)
    }
}
