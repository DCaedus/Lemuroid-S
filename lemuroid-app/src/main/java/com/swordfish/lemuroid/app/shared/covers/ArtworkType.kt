package com.swordfish.lemuroid.app.shared.covers

/**
 * Kinds of artwork available on the libretro thumbnails server. The folder names match the ones
 * RetroArch uses for its playlists.
 */
enum class ArtworkType(val prefValue: String, val libretroFolder: String) {
    BOX_ART("boxart", "Named_Boxarts"),
    SCREENSHOT("snap", "Named_Snaps"),
    TITLE_SCREEN("title", "Named_Titles"),
    ;

    companion object {
        /** Stored in per-console settings when the console follows the global artwork type. */
        const val PREF_VALUE_DEFAULT = "default"

        val DEFAULT = BOX_ART

        fun fromPrefValue(value: String?): ArtworkType? = values().firstOrNull { it.prefValue == value }
    }
}
