package com.swordfish.lemuroid.app.shared.covers

/**
 * Kinds of artwork available on the libretro thumbnails server.
 * [libretroFolder]: the server's and RetroArch's folder name. [folderName]: Lemuroid-S's own folder
 * name, Lemuroid-S/covers/<system>/<folderName>.
 */
enum class ArtworkType(val prefValue: String, val libretroFolder: String, val folderName: String) {
    BOX_ART("boxart", "Named_Boxarts", "boxarts"),
    SCREENSHOT("snap", "Named_Snaps", "screenshots"),
    TITLE_SCREEN("title", "Named_Titles", "titles"),
    ;

    companion object {
        /** Stored in per-console settings when the console follows the global artwork type. */
        const val PREF_VALUE_DEFAULT = "default"

        val DEFAULT = BOX_ART

        fun fromPrefValue(value: String?): ArtworkType? = values().firstOrNull { it.prefValue == value }

        fun fromLibretroFolder(name: String): ArtworkType? = values().firstOrNull { it.libretroFolder == name }
    }
}
