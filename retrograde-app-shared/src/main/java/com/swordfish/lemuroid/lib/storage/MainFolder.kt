package com.swordfish.lemuroid.lib.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Lemuroid-S's main folder in phone storage ("Lemuroid-S"), laid out like RetroArch's:
 *
 *   Lemuroid-S/roms/<system>/      ROMs, one folder per system (ES-DE names), if chosen
 *   Lemuroid-S/saves/              in-game (battery) saves, <rom name>.srm
 *   Lemuroid-S/states/<core>/      save states
 *   Lemuroid-S/thumbnails/<system>/Named_Boxarts|Named_Snaps|Named_Titles/
 *
 * The user picks it once with Android's folder picker; without full file access Android doesn't let
 * apps create folders in storage root on their own.
 */
class MainFolder(private val appContext: Context) {
    private fun preferences() = SharedPreferencesHelper.getSharedPreferences(appContext)

    fun isSet(): Boolean = getRoot() != null

    fun getRoot(): DocumentFolder? {
        val value = preferences().getString(appContext.getString(R.string.pref_key_main_folder), null) ?: return null
        return DocumentFolder(appContext.contentResolver, Uri.parse(value))
    }

    /** The tree uri Android granted permission for (kept when other folder permissions are released). */
    fun getTreeUri(): String? = preferences().getString(appContext.getString(R.string.pref_key_main_folder_tree), null)

    /**
     * Uses the folder picked by the user. If it isn't called "Lemuroid-S", a "Lemuroid-S" folder is
     * found or created inside it. Returns false if the folder can't be used.
     */
    fun setFromPickedTree(treeUri: Uri): Boolean {
        val pickedUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val picked = DocumentFolder(appContext.contentResolver, pickedUri)
        val pickedName = displayName(appContext, pickedUri)

        val root =
            if (pickedName == FOLDER_NAME) {
                picked
            } else {
                runCatching { picked.subFolder(FOLDER_NAME) }.getOrNull()
            } ?: return false

        DocumentFolder.clearKnownFolders()
        preferences().edit()
            .putString(appContext.getString(R.string.pref_key_main_folder), root.uri.toString())
            .putString(appContext.getString(R.string.pref_key_main_folder_tree), treeUri.toString())
            .apply()
        return true
    }

    /**
     * Older test builds could create duplicate folders like "gba (1)". Moves their files into the
     * original folder and removes the duplicate. Only for folders Lemuroid-S manages (thumbnails,
     * saves, states), never ROMs.
     */
    fun mergeDuplicateFolders() {
        val root = getRoot() ?: return
        for (name in listOf(THUMBNAILS, SAVES, STATES)) {
            val folder = runCatching { root.subFolder(name, create = false) }.getOrNull() ?: continue
            runCatching { mergeDuplicates(folder, depth = 0) }
        }
    }

    private fun mergeDuplicates(
        folder: DocumentFolder,
        depth: Int,
    ) {
        val children = folder.children()
        for (duplicate in children.values.filter { it.isDirectory }) {
            val baseName = DUPLICATE_NAME.matchEntire(duplicate.name)?.groupValues?.get(1) ?: continue
            val original = children[baseName]?.takeIf { it.isDirectory } ?: continue
            copyMissing(
                DocumentFolder(appContext.contentResolver, duplicate.uri),
                DocumentFolder(appContext.contentResolver, original.uri),
            )
            folder.delete(duplicate)
        }
        if (depth >= MAX_MERGE_DEPTH) return
        folder.children().values
            .filter { it.isDirectory }
            .forEach { mergeDuplicates(DocumentFolder(appContext.contentResolver, it.uri), depth + 1) }
    }

    private fun copyMissing(
        from: DocumentFolder,
        to: DocumentFolder,
    ) {
        val existing = to.children()
        for (child in from.children().values) {
            if (child.isDirectory) {
                val target = to.subFolder(child.name) ?: continue
                copyMissing(DocumentFolder(appContext.contentResolver, child.uri), target)
            } else if (child.name !in existing) {
                from.read(child)?.let { to.write(child.name, it, null) }
            }
        }
    }

    fun folder(
        vararg path: String,
        create: Boolean = true,
    ): DocumentFolder? {
        val root = getRoot() ?: return null
        return runCatching { root.subFolder(path.toList(), create) }.getOrNull()
    }

    companion object {
        const val FOLDER_NAME = "Lemuroid-S"
        private val DUPLICATE_NAME = Regex("""^(.+) \(\d+\)$""")
        private const val MAX_MERGE_DEPTH = 2
        const val ROMS = "roms"
        const val BIOS = "bios"
        const val SAVES = "saves"
        const val STATES = "states"
        const val THUMBNAILS = "thumbnails"

        /** ROM folder names per system, following ES-DE's naming. */
        val SYSTEM_FOLDERS: Map<SystemID, String> =
            mapOf(
                SystemID.ATARI2600 to "atari2600",
                SystemID.ATARI7800 to "atari7800",
                SystemID.LYNX to "atarilynx",
                SystemID.NES to "nes",
                SystemID.SNES to "snes",
                SystemID.GB to "gb",
                SystemID.GBC to "gbc",
                SystemID.GBA to "gba",
                SystemID.GENESIS to "genesis",
                SystemID.SEGACD to "segacd",
                SystemID.SMS to "mastersystem",
                SystemID.GG to "gamegear",
                SystemID.N64 to "n64",
                SystemID.PSX to "psx",
                SystemID.PSP to "psp",
                SystemID.FBNEO to "fbneo",
                SystemID.MAME2003PLUS to "mame",
                SystemID.NDS to "nds",
                SystemID.PC_ENGINE to "pcengine",
                SystemID.NGP to "ngp",
                SystemID.NGC to "ngpc",
                SystemID.WS to "wonderswan",
                SystemID.WSC to "wonderswancolor",
                SystemID.DOS to "dos",
                SystemID.NINTENDO_3DS to "n3ds",
            )

        /** ES-DE folder name for a system id (as stored in games), e.g. "md" -> "genesis". */
        fun systemFolderName(systemDbName: String): String {
            val systemId = SystemID.values().firstOrNull { it.dbname == systemDbName }
            return systemId?.let { SYSTEM_FOLDERS[it] } ?: systemDbName
        }

        /** Creates one subfolder per system, but only if [romsFolder] is empty (never touches existing files). */
        fun createSystemFoldersIfEmpty(romsFolder: DocumentFolder) {
            if (!romsFolder.isEmpty()) return
            SYSTEM_FOLDERS.values.sorted().forEach { runCatching { romsFolder.subFolder(it) } }
        }

        /** Display name of a folder, for both picked trees and subfolders inside them. */
        fun displayName(
            context: Context,
            uri: Uri,
        ): String? {
            val documentUri =
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    uri
                } else {
                    DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                }
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return runCatching {
                context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
    }
}
