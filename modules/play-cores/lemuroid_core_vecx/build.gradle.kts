// Lemuroid-S Play module for the vecx core. Generated like the others; see buildSrc/PlayCores.kt.
plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.swordfish.lemuroid.core.vecx"
    defaultConfig {
        missingDimensionStrategy("opensource", "play")
        missingDimensionStrategy("cores", "dynamic")
    }
    packaging {
        jniLibs {
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
        }
    }
    sourceSets["main"].jniLibs.srcDir(PlayCores.libsDir(project))
}

dependencies {
    implementation(project(":lemuroid-app"))
}

PlayCores.registerFetch(project, "vecx")
