package com.swordfish.lemuroid.app.shared.covers

/**
 * A game's artwork in RetroArch's thumbnails layout: `<systemFolder>/<artwork folder>/<name>.png`.
 * [names] are already sanitized; the first is the exact name, the others are fallbacks without tags.
 */
data class CoverTarget(
    val systemFolder: String,
    val artworkType: ArtworkType,
    val names: List<String>,
) {
    val exactName: String get() = names.first()
}
