// Lemuroid-S: emulator cores shipped inside the app, so the most popular systems work without a
// download: FCEUmm (NES), Snes9x (SNES), Gambatte (GB/GBC), mGBA (GBA), PCSX ReARMed (PS1).
// The libraries are libretro buildbot nightlies (buildbot.libretro.com/nightly/android) from 2026-11-24.
// All other cores are downloaded on demand (Google Play in the Play build, GitHub otherwise).

plugins {
    id("com.android.library")
}

android {
    namespace = "com.swordfish.lemuroid.includedcores"
}
