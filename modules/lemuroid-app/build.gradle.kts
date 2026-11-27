plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    defaultConfig {
        // Lemuroid-S: versionCode = major * 10000 + minor * 100 + patch (1.2.0 -> 10200); each
        // version has its changelog in fastlane/metadata/android/en-US/changelogs/<versionCode>.txt.
        versionCode = 10200
        versionName = "1.2.0"
        applicationId = "com.dcaedus.lemuroids"
    }
    flavorDimensions += listOf("opensource", "cores")

    if (usePlayDynamicFeatures()) {
        println("Building Google Play version. Bundling dynamic features.")
        dynamicFeatures.addAll(PlayCores.moduleNames(rootDir).map { ":$it" })
    }

    // Since some dependencies are closed source we make a completely free as in free speech variant.

    productFlavors {

        create("free") {
            dimension = "opensource"
        }

        create("play") {
            dimension = "opensource"
        }

        // Lemuroid-S "full" GitHub APKs: every core inside the app, no downloads. One APK per processor
        // type (see splits below), since all cores for all of them would make a ~250 MB APK.
        create("bundle") {
            dimension = "cores"
        }

        // Cores ship in the app (5) or download on demand (Google Play modules or the "cores" branch on GitHub).
        create("dynamic") {
            dimension = "cores"
        }
    }

    // Lemuroid-S: the full APKs are split by processor type (lemuroid-s-<version>-full-arm64-v8a.apk...).
    // Splits apply to the whole module, so they're only turned on when building the full APKs; the
    // regular APK stays a single one for every device.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("FreeBundle") }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packagingOptions {
        jniLibs {
            // Stripping created some issues with some libretro cores such as ppsspp
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module", "META-INF/INDEX.LIST")
        }
    }

    signingConfigs {
        maybeCreate("debug").apply {
            storeFile = file("debug.keystore")
        }

        // Lemuroid-S: the release key is never stored in git. Its location and passwords come from
        // environment variables (CI secrets) or ~/.gradle/gradle.properties:
        // LEMUROIDS_KEYSTORE, LEMUROIDS_KEYSTORE_PASSWORD, LEMUROIDS_KEY_ALIAS, LEMUROIDS_KEY_PASSWORD.
        // Without them, release builds are signed with the debug key (fine for local testing only).
        val releaseKeystore = releaseSigning("LEMUROIDS_KEYSTORE")
        maybeCreate("release").apply {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = releaseSigning("LEMUROIDS_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning("LEMUROIDS_KEY_ALIAS")
                keyPassword = releaseSigning("LEMUROIDS_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs["release"]
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "lemuroid_name", "Lemuroid-S")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "lemuroid_name", "Lemuroid-S Debug")
        }
    }

    // Lemuroid-S: no Google-encrypted dependency list in the APK (IzzyOnDroid flags it as an unknown
    // blob); the Play bundle keeps it, since Play uses it for dependency warnings.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    // Lemuroid-S: lists the app's languages so Android 13+ offers a per-app language
    // (Settings > Apps > Lemuroid-S > Language, also linked from the app's Settings).
    androidResources {
        generateLocaleConfig = true
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    namespace = "com.swordfish.lemuroid"
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":lemuroid-metadata-libretro-db"))
    implementation(project(":lemuroid-touchinput"))

    "baselineProfile"(project(":baselineprofile"))
    implementation(deps.libs.androidx.profileInstaller)

    // Lemuroid-S: the most popular cores ship with the app; the rest download on demand.
    implementation(project(":lemuroid-included-cores"))
    "bundleImplementation"(project(":bundled-cores"))

    "freeImplementation"(project(":lemuroid-app-ext-free"))
    "playImplementation"(project(":lemuroid-app-ext-play"))

    implementation(deps.libs.androidx.navigation.navigationFragment)
    implementation(deps.libs.androidx.navigation.navigationUi)
    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)


    implementation(deps.libs.androidx.leanback.leanback)
    implementation(deps.libs.androidx.leanback.leanbackPreference)
    implementation(deps.libs.androidx.leanback.leanbackPaging)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.androidx.documentfile)
    // WorkManager's ListenableFuture: the Play build's Google libraries otherwise leave only an empty stub.
    implementation(deps.libs.guava)
    implementation(deps.libs.androidx.leanback.tvProvider)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)
    implementation(deps.libs.composeHtmlText)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    implementation(project(":libretrodroid"))

    // Uncomment this when using a local aar file.
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    ksp(deps.libs.dagger.android.processor)
    ksp(deps.libs.dagger.compiler)
}

fun releaseSigning(name: String): String? = System.getenv(name) ?: findProperty(name) as String?

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}
