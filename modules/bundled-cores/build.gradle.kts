// Lemuroid-S: every on-demand core inside the app, for the "full" GitHub APK (bundle flavor).
// Like the Play modules, the libraries are downloaded at build time from the cores branch (see buildSrc/PlayCores.kt).
plugins {
    id("com.android.library")
}

android {
    namespace = "com.swordfish.lemuroid.bundledcores"

    val coresDir = layout.buildDirectory.dir("bundledCores").get().asFile
    PlayCores.coreNames(rootDir).forEach { core ->
        val dir = File(coresDir, core)
        PlayCores.registerFetch(project, core, "fetchCore_$core", dir, placeholders = false)
        sourceSets["main"].jniLibs.srcDir(dir)
    }

    // PPSSPP's support files, so PSP games need no download either.
    val assetsDir = layout.buildDirectory.dir("bundledCoreAssets").get().asFile
    PlayCores.registerFetchFile(project, "ppsspp-assets.zip", assetsDir)
    sourceSets["main"].assets.srcDir(assetsDir)
}
