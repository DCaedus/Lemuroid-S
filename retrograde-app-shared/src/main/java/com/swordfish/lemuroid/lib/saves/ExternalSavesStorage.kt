package com.swordfish.lemuroid.lib.saves

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import timber.log.Timber
import java.io.File

/**
 * Where in-game (battery) saves and save states are kept in phone storage ("Select SAVES folder"),
 * like RetroArch's save directories:
 *  - Lemuroid-S folder (default): Lemuroid-S/saves/<system>/<rom>.srm and Lemuroid-S/states/<system>/...
 *  - ROMs folder: next to each game's ROM
 *
 * The folder above is the only copy. Lemuroid-S's private app storage (Android/data) is used only
 * while the folder can't be reached (not chosen yet, access lost); those files move into the
 * folder as soon as it is available again.
 */
class ExternalSavesStorage(private val appContext: Context) {
    enum class Location(val prefValue: String) {
        MAIN_FOLDER("main"),
        ROMS_FOLDER("roms"),
    }

    val mainFolder = MainFolder(appContext)

    private val contentResolver: ContentResolver get() = appContext.contentResolver

    fun getLocation(): Location {
        val value = preferences().getString(appContext.getString(R.string.pref_key_saves_location), null)
        return Location.values().firstOrNull { it.prefValue == value } ?: Location.MAIN_FOLDER
    }

    /** Folder for [game]'s battery save, or null if saves are only kept in app storage. */
    fun savesFolder(game: Game): SaveFolder? {
        return when (getLocation()) {
            Location.MAIN_FOLDER ->
                mainFolder.folder(
                    MainFolder.SAVES,
                    systemFolder(game),
                )?.let { SaveFolder.Document(it) }
            Location.ROMS_FOLDER -> romFolder(game)
        }
    }

    /** Folder for [game]'s save states with core [coreName], or null if only kept in app storage. */
    fun statesFolder(
        game: Game,
        coreName: String,
    ): SaveFolder? {
        return when (getLocation()) {
            Location.MAIN_FOLDER ->
                mainFolder.folder(
                    MainFolder.STATES,
                    systemFolder(game),
                )?.let { SaveFolder.Document(it) }
            Location.ROMS_FOLDER -> romFolder(game)
        }
    }

    /**
     * Where "Import Saves from other Emulators" puts RetroArch's files, as they are (RetroArch's
     * names, no sorting): the top of Lemuroid-S/saves and Lemuroid-S/states, or the top of the ROMs
     * folder when saves are kept in the ROMs folder. Lemuroid-S also looks here when a game opens.
     */
    fun importedSavesFolder(): SaveFolder? = importFolder(MainFolder.SAVES)

    fun importedStatesFolder(): SaveFolder? = importFolder(MainFolder.STATES)

    /**
     * Where to put an imported file whose system is known ([systemFolder], ES-DE name): the
     * system's saves/states folder, or that system's folder inside the ROMs folder (if it exists).
     * Falls back to the top folder.
     */
    fun importTargetFolder(
        isStates: Boolean,
        systemFolder: String?,
    ): SaveFolder? {
        val kind = if (isStates) MainFolder.STATES else MainFolder.SAVES
        if (systemFolder == null) return importFolder(kind)
        val folder =
            when (getLocation()) {
                Location.MAIN_FOLDER -> mainFolder.folder(kind, systemFolder)
                Location.ROMS_FOLDER -> romsRootFolder()?.let { it.subFolder(systemFolder, create = false) ?: it }
            }
        return folder?.let { SaveFolder.Document(it) }
    }

    private fun importFolder(mainFolderName: String): SaveFolder? {
        val folder =
            when (getLocation()) {
                Location.MAIN_FOLDER -> mainFolder.folder(mainFolderName)
                Location.ROMS_FOLDER -> romsRootFolder()
            }
        return folder?.let { SaveFolder.Document(it) }
    }

    private fun romsRootFolder(): DocumentFolder? {
        @Suppress("DEPRECATION")
        val value =
            SharedPreferencesHelper.getLegacySharedPreferences(appContext)
                .getString(appContext.getString(R.string.pref_key_extenral_folder), null) ?: return null
        val uri = Uri.parse(value)
        val folderUri =
            if (DocumentsContract.isDocumentUri(appContext, uri)) {
                uri
            } else {
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            }
        return DocumentFolder(contentResolver, folderUri)
    }

    private fun systemFolder(game: Game): String = MainFolder.systemFolderName(game.systemId)

    private fun romFolder(game: Game): SaveFolder? {
        val romUri = Uri.parse(game.fileUri)
        if (romUri.scheme == ContentResolver.SCHEME_FILE) {
            return romUri.path?.let { File(it).parentFile }?.let { SaveFolder.Local(it) }
        }
        if (romUri.scheme != ContentResolver.SCHEME_CONTENT) return null

        val parentId = findParentDocumentId(romUri) ?: return null
        val folderUri = DocumentsContract.buildDocumentUriUsingTree(romUri, parentId)
        return SaveFolder.Document(DocumentFolder(contentResolver, folderUri))
    }

    private fun findParentDocumentId(documentUri: Uri): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = runCatching { DocumentsContract.findDocumentPath(contentResolver, documentUri) }.getOrNull()
            val ids = path?.path
            if (ids != null && ids.size >= 2) return ids[ids.size - 2]
        }
        // Fallback for local storage providers, whose ids look like "primary:Roms/GBA/game.gba".
        val documentId = DocumentsContract.getDocumentId(documentUri)
        return documentId.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
    }

    private fun preferences() = SharedPreferencesHelper.getSharedPreferences(appContext)

    /** A save file found in the chosen folder. */
    data class ExternalFile(val data: ByteArray, val lastModified: Long)

    /**
     * A folder holding saves or states. Its file listing is read once and remembered until the
     * next write or delete, so use one instance per operation (a game load, a save...).
     */
    sealed interface SaveFolder {
        /** File name -> last modified time, for the files in this folder. */
        fun lastModifiedTimes(): Map<String, Long>

        fun read(fileName: String): ExternalFile?

        /** Writes [fileName] so an interrupted write never leaves a half-written file. */
        fun write(
            fileName: String,
            data: ByteArray,
        )

        fun delete(fileName: String)

        class Local(private val directory: File) : SaveFolder {
            override fun lastModifiedTimes(): Map<String, Long> {
                return directory.listFiles()?.associate { it.name to it.lastModified() } ?: emptyMap()
            }

            override fun read(fileName: String): ExternalFile? {
                val file = File(directory, fileName)
                return if (file.exists()) ExternalFile(file.readBytes(), file.lastModified()) else null
            }

            override fun write(
                fileName: String,
                data: ByteArray,
            ) {
                File(directory, fileName).writeBytesAtomic(data)
            }

            override fun delete(fileName: String) {
                File(directory, fileName).delete()
            }
        }

        class Document(private val folder: DocumentFolder) : SaveFolder {
            private var listing: Map<String, DocumentFolder.Child>? = null

            private fun children(): Map<String, DocumentFolder.Child> {
                return listing ?: folder.children().also { listing = it }
            }

            override fun lastModifiedTimes(): Map<String, Long> {
                val children = children()
                val times = children.mapValues { it.value.lastModified }.toMutableMap()
                // A write interrupted before its last step leaves only "<name>.tmp" (complete data).
                children.keys
                    .filter { it.endsWith(DocumentFolder.TEMP_SUFFIX) }
                    .map { it.removeSuffix(DocumentFolder.TEMP_SUFFIX) }
                    .filter { it !in children }
                    .forEach { times[it] = children.getValue(it + DocumentFolder.TEMP_SUFFIX).lastModified }
                return times
            }

            override fun read(fileName: String): ExternalFile? {
                val children = children()
                val child = children[fileName] ?: children[fileName + DocumentFolder.TEMP_SUFFIX] ?: return null
                val data = folder.read(child) ?: return null
                return ExternalFile(data, child.lastModified)
            }

            override fun write(
                fileName: String,
                data: ByteArray,
            ) {
                try {
                    folder.writeSafely(fileName, data, children())
                } finally {
                    listing = null
                }
            }

            override fun delete(fileName: String) {
                children()[fileName]?.let { folder.deleteFile(it.uri) }
                listing = null
            }
        }
    }

    companion object {
        /** Logs and swallows errors (callers fall back to app storage). */
        inline fun <T> safely(
            what: String,
            block: () -> T,
        ): T? {
            return runCatching(block)
                .onFailure { Timber.w(it, "External saves: $what failed") }
                .getOrNull()
        }
    }
}
