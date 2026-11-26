plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.swordfish.lemuroid.ext"
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))

    implementation(deps.libs.retrofit)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
}
