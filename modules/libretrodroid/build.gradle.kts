// LibretroDroid (https://github.com/Swordfish90/LibretroDroid) by Swordfish90, GPL-3.0.
// Bundled into Lemuroid-S as a module so we can modify it (e.g. audio volume control).
// Based on LibretroDroid 0.14.0.

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.swordfish.libretrodroid"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(kotlin(deps.libs.kotlin.stdlib))
    implementation(deps.libs.androidx.lifecycle.runtime)
}
