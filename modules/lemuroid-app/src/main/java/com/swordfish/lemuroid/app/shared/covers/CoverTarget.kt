package com.swordfish.lemuroid.app.shared.covers

/**
 * A game's artwork: `covers/<system>/<boxarts|screenshots|titles>/<name>.png` in Lemuroid-S
 * (downloaded from the libretro server, which uses RetroArch's `Named_*` folder names).
 * [systemFolder] is RetroArch's/libretro's console name (used online and in RetroArch's folder),
 * [mainFolderSystem] is the ES-DE name used in the Lemuroid-S folder (e.g. "gba").
 * [names] are already sanitized; the first is the exact name, the others are fallbacks without tags.
 */
data class CoverTarget(
    val systemFolder: String,
    val mainFolderSystem: String,
    val artworkType: ArtworkType,
    val names: List<String>,
) {
    val exactName: String get() = names.first()
}
