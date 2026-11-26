package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * "Import RetroArch Covers": copies artwork from RetroArch's thumbnails folder
 * (thumbnails/<RetroArch console name>/Named_Boxarts|Named_Snaps|Named_Titles) into Lemuroid-S's
 * covers/<system>/boxarts|screenshots|titles, for the systems Lemuroid-S runs. Existing files are kept.
 */
class RetroArchCoversImporter(private val appContext: Context) {
    /** Number of covers copied, or null if the Lemuroid-S folder or RetroArch's thumbnails aren't available. */
    suspend fun importFrom(treeUri: Uri): Int? =
        withContext(Dispatchers.IO) {
            val mainFolder = MainFolder(appContext)
            if (!mainFolder.isSet()) return@withContext null

            val picked =
                DocumentFolder(
                    appContext.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                )
            val thumbnails =
                if (MainFolder.displayName(appContext, picked.uri) == THUMBNAILS) {
                    picked
                } else {
                    picked.subFolder(THUMBNAILS, create = false)
                } ?: return@withContext null

            var copied = 0
            for (console in thumbnails.children().values.filter { it.isDirectory }) {
                val system = mainFolderSystem(console.name) ?: continue
                val consoleFolder = DocumentFolder(appContext.contentResolver, console.uri)
                for (type in consoleFolder.children().values.filter { it.isDirectory && it.name in ARTWORK_FOLDERS }) {
                    val artworkType = ArtworkType.fromLibretroFolder(type.name) ?: continue
                    val target = mainFolder.folder(MainFolder.COVERS, system, artworkType.folderName) ?: continue
                    copied += copyMissing(DocumentFolder(appContext.contentResolver, type.uri), target)
                }
            }
            CoverStore.get(appContext).forgetMainFolderListings()
            copied
        }

    private fun copyMissing(
        from: DocumentFolder,
        to: DocumentFolder,
    ): Int {
        val existing = to.children().keys
        var copied = 0
        for (file in from.children().values) {
            if (file.isDirectory || file.name in existing || !file.name.endsWith(".png", ignoreCase = true)) continue
            runCatching { from.read(file)?.let { to.write(file.name, it, null) } }
                .onSuccess { if (it != null) copied++ }
                .onFailure { Timber.w(it, "Could not copy cover ${file.name}") }
        }
        return copied
    }

    /** RetroArch uses libretro console names ("Nintendo - Game Boy Advance"); Lemuroid-S uses ES-DE's ("gba"). */
    private fun mainFolderSystem(retroArchName: String): String? {
        if (retroArchName == "MAME") return MainFolder.SYSTEM_FOLDERS[SystemID.MAME2003PLUS]
        val system = GameSystem.all().firstOrNull { it.libretroFullName == retroArchName } ?: return null
        return MainFolder.SYSTEM_FOLDERS[system.id]
    }

    companion object {
        /** RetroArch's own folder name. */
        private const val THUMBNAILS = "thumbnails"
        private val ARTWORK_FOLDERS = ArtworkType.values().map { it.libretroFolder }.toSet()
    }
}
