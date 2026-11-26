// Lemuroid-S Play module for the genesis_plus_gx core. Generated like the others; see buildSrc/PlayCores.kt.
plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.swordfish.lemuroid.core.genesis_plus_gx"
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

PlayCores.registerFetch(project, "genesis_plus_gx")
