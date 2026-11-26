@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

// Lemuroid-S: all modules live in the modules/ folder (keeps the repository's main page short).
listOf(
    "retrograde-util",
    "retrograde-app-shared",
    "lemuroid-touchinput",
    "lemuroid-included-cores",
    "libretrodroid",
    "lemuroid-app",
    "lemuroid-metadata-libretro-db",
    "lemuroid-app-ext-free",
    "lemuroid-app-ext-play",
    "baselineprofile",
    "bundled-cores",
).forEach { name ->
    include(":$name")
    project(":$name").projectDir = File(rootDir, "modules/$name")
}

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

// Lemuroid-S: on-demand Play core modules live in play-cores/ (libraries downloaded at build time from the
// "cores" branch, see buildSrc/PlayCores.kt).
if (usePlayDynamicFeatures()) {
    File(rootDir, "modules/play-cores").listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("lemuroid_core_") && File(it, "build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { module ->
            include(":${module.name}")
            project(":${module.name}").projectDir = module
        }
}
