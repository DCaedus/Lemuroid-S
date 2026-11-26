@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include(
    ":retrograde-util",
    ":retrograde-app-shared",
    ":lemuroid-touchinput",
    ":lemuroid-included-cores",
    ":libretrodroid",
    ":lemuroid-app",
    ":lemuroid-metadata-libretro-db",
    ":lemuroid-app-ext-free",
    ":lemuroid-app-ext-play",
    ":bundled-cores",
    ":baselineprofile"
)

project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

// Lemuroid-S: on-demand Play core modules live in play-cores/ (libraries downloaded at build time from the
// "cores" branch, see buildSrc/PlayCores.kt), replacing the LemuroidCores 1.17.0 modules.
if (usePlayDynamicFeatures()) {
    File(rootDir, "play-cores").listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("lemuroid_core_") && File(it, "build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { module ->
            include(":${module.name}")
            project(":${module.name}").projectDir = module
        }
}
