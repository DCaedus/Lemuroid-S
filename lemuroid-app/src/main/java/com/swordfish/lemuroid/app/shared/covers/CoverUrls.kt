package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.MainFolder

/**
 * Builds the list of URLs to try for a game's artwork, following RetroArch's thumbnail method:
 *  - the image is named after the game's database name, with the characters &*\/:`<>?\|" replaced by _
 *  - if no exact match exists, it retries without [..] tags and then without (..) tags too,
 *    like RetroArch's "Thumbnail Matching" options
 *  - each name is tried on the libretro thumbnails server first, then on libretro's GitHub copy
 */
object CoverUrls {
    private const val LIBRETRO_SERVER = "https://thumbnails.libretro.com"
    private const val GITHUB_SERVER = "https://raw.githubusercontent.com/libretro-thumbnails"

    private val FORBIDDEN_CHARACTERS = Regex("[&*/:`<>?\\\\|\"]")
    private val SQUARE_BRACKET_TAGS = Regex("\\s*\\[[^]]*]")
    private val ROUND_BRACKET_TAGS = Regex("\\s*\\([^)]*\\)")

    /** Which artwork to look for: console folder, artwork type and file names, like RetroArch. */
    fun target(
        context: Context,
        game: Game,
    ): CoverTarget? {
        val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return null
        val names = nameVariants(game.title)
        if (names.isEmpty()) return null
        val mainFolderSystem = MainFolder.SYSTEM_FOLDERS[system.id] ?: system.id.dbname
        return CoverTarget(thumbnailSystemName(system), mainFolderSystem, artworkType(context, system), names)
    }

    /** Ordered list of artwork URLs to try for [game]. Empty when nothing can be computed. */
    fun candidates(
        context: Context,
        game: Game,
    ): List<String> {
        return target(context, game)?.let { candidates(it) } ?: emptyList()
    }

    fun candidates(target: CoverTarget): List<String> {
        return target.names.flatMap { name ->
            listOf(
                libretroServerUrl(target.systemFolder, target.artworkType, name),
                githubUrl(target.systemFolder, target.artworkType, name),
            )
        }
    }

    /** The artwork type for [system]: its own setting if set, otherwise the global one. */
    fun artworkType(
        context: Context,
        system: GameSystem,
    ): ArtworkType {
        val preferences = SharedPreferencesHelper.getSharedPreferences(context)
        val systemValue = preferences.getString(systemArtworkKey(context, system), ArtworkType.PREF_VALUE_DEFAULT)
        val globalValue = preferences.getString(context.getString(R.string.pref_key_artwork_type), null)
        return ArtworkType.fromPrefValue(systemValue)
            ?: ArtworkType.fromPrefValue(globalValue)
            ?: ArtworkType.DEFAULT
    }

    fun systemArtworkKey(
        context: Context,
        system: GameSystem,
    ): String {
        return context.getString(R.string.pref_key_artwork_type_system_prefix) + system.id.dbname
    }

    private fun thumbnailSystemName(system: GameSystem): String {
        // The specific MAME version Lemuroid uses has no folder of its own on the server.
        return if (system.id == SystemID.MAME2003PLUS) "MAME" else system.libretroFullName
    }

    private fun nameVariants(gameName: String): List<String> {
        val exact = gameName.trim()
        val withoutSquareTags = exact.replace(SQUARE_BRACKET_TAGS, "").trim()
        val withoutAnyTags = withoutSquareTags.replace(ROUND_BRACKET_TAGS, "").trim()
        return listOf(exact, withoutSquareTags, withoutAnyTags)
            .filter { it.isNotBlank() }
            .distinct()
            .map { it.replace(FORBIDDEN_CHARACTERS, "_") }
    }

    private fun libretroServerUrl(
        systemName: String,
        artworkType: ArtworkType,
        name: String,
    ): String {
        return "$LIBRETRO_SERVER/${Uri.encode(systemName)}/${artworkType.libretroFolder}/${Uri.encode(name)}.png"
    }

    private fun githubUrl(
        systemName: String,
        artworkType: ArtworkType,
        name: String,
    ): String {
        // GitHub repositories use underscores instead of spaces, e.g. "Nintendo_-_Game_Boy".
        val repository = systemName.replace(" ", "_")
        return "$GITHUB_SERVER/${Uri.encode(repository)}/master/${artworkType.libretroFolder}/${Uri.encode(name)}.png"
    }
}
