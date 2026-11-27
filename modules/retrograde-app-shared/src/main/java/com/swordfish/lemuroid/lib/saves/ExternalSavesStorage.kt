package com.swordfish.lemuroid.lib.saves

import android.content.Context
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import timber.log.Timber
import java.io.File

/**
 * Where in-game (battery) saves and save states are kept in phone storage, like RetroArch's save
 * directories:
 * Lemuroid-S/saves/<system>/<rom>.srm and Lemuroid-S/states/<system>/... (ROMs-folder saves were
 * removed: they can't be synced with Google Drive).
 *
 * The folder above is the only copy. Lemuroid-S's private app storage (Android/data) is used only
 * while the folder can't be reached (not chosen yet, access lost); those files move into the
 * folder as soon as it is available again.
 */
class ExternalSavesStorage(private val appContext: Context) {
    val mainFolder = MainFolder(appContext)

    /** Folder for [game]'s battery save, or null if the Lemuroid-S folder can't be reached. */
    fun savesFolder(game: Game): SaveFolder? {
        return mainFolder.folder(MainFolder.SAVES, systemFolder(game))?.let { SaveFolder.Document(it) }
    }

    /** Folder for [game]'s save states, or null if the Lemuroid-S folder can't be reached. */
    @Suppress("UNUSED_PARAMETER")
    fun statesFolder(
        game: Game,
        coreName: String,
    ): SaveFolder? {
        return mainFolder.folder(MainFolder.STATES, systemFolder(game))?.let { SaveFolder.Document(it) }
    }

    /**
     * Where "Import Saves from other Emulators" puts files whose system isn't known, as they are:
     * the top of Lemuroid-S/saves and Lemuroid-S/states. Lemuroid-S also looks here when a game opens.
     */
    fun importedSavesFolder(): SaveFolder? = importFolder(MainFolder.SAVES)

    fun importedStatesFolder(): SaveFolder? = importFolder(MainFolder.STATES)

    /**
     * Where to put an imported file whose system is known ([systemFolder], ES-DE name): that
     * system's saves/states folder. Falls back to the top folder.
     */
    fun importTargetFolder(
        isStates: Boolean,
        systemFolder: String?,
    ): SaveFolder? {
        val kind = if (isStates) MainFolder.STATES else MainFolder.SAVES
        if (systemFolder == null) return importFolder(kind)
        return mainFolder.folder(kind, systemFolder)?.let { SaveFolder.Document(it) }
    }

    private fun importFolder(mainFolderName: String): SaveFolder? {
        return mainFolder.folder(mainFolderName)?.let { SaveFolder.Document(it) }
    }

    private fun systemFolder(game: Game): String = MainFolder.systemFolderName(game.systemId)

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
            @Volatile
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
                .onFailure {
                    Timber.w(it, "External saves: $what failed")
                    // Lemuroid-S: a remembered folder may have been deleted or moved outside the app;
                    // look folders up again next time instead of failing until the app restarts.
                    com.swordfish.lemuroid.lib.storage.DocumentFolder.clearKnownFolders()
                }
                .getOrNull()
        }
    }
}
