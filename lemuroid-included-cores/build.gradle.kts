// Lemuroid-S: emulator cores shipped inside the app, so the most popular systems work without a
// download: FCEUmm (NES), Snes9x (SNES), Gambatte (GB/GBC), mGBA (GBA), PCSX ReARMed (PS1).
// The libraries come from Swordfish90/LemuroidCores at tag 1.17.0 (the cores version Lemuroid-S uses).
// All other cores are downloaded on demand (Google Play in the Play build, GitHub otherwise).

plugins {
    id("com.android.library")
}

android {
    namespace = "com.swordfish.lemuroid.includedcores"
}
