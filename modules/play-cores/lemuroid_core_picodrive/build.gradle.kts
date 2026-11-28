// Lemuroid-S Play module for the picodrive core. Generated like the others; see buildSrc/PlayCores.kt.
plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.swordfish.lemuroid.core.picodrive"
    defaultConfig {
        missingDimensionStrategy("opensource", "play")
        missingDimensionStrategy("cores", "dynamic")
    }
    packaging {
        jniLibs {
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            // Like the app: uncompressed in the module's APK, loaded from there (NativeLibraries).
            useLegacyPackaging = false
        }
    }
    sourceSets["main"].jniLibs.srcDir(PlayCores.libsDir(project))
}

dependencies {
    implementation(project(":lemuroid-app"))
}

PlayCores.registerFetch(project, "picodrive")
