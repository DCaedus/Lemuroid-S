package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds game artwork, in this order (like RetroArch):
 *  1. RetroArch's thumbnails folder, if the user picked it in Settings (read only)
 *  2. Lemuroid-S's thumbnails folder: Lemuroid-S/thumbnails/<system (ES-DE name)>/Named_* in phone
 *     storage once the Lemuroid-S folder is chosen, otherwise the app's private storage
 *  3. Download from libretro (server, then GitHub backup) and save it into folder 2
 *
 * Both folders use RetroArch's layout: thumbnails/<console>/Named_Boxarts|Named_Snaps|Named_Titles/<game>.png
 */
class CoverStore(private val appContext: Context) {
    data class Resolved(val data: Any, val downloaded: Boolean)

    private val httpClient by lazy { OkHttpClient() }

    /** Artwork that couldn't be found or downloaded in this session, so it isn't retried on every scroll. */
    private val missing = Collections.newSetFromMap(ConcurrentHashMap<CoverTarget, Boolean>())

    private val mainFolder = MainFolder(appContext)

    /** Lemuroid-S/thumbnails folder listings, cached per "<console>/<artwork folder>". */
    private val mainFolderListings = ConcurrentHashMap<String, MainFolderListing>()
    private var mainFolderListingsRoot: String? = null

    private class MainFolderListing(val folder: DocumentFolder?, val files: MutableMap<String, Uri>)

    /** RetroArch folder listings, cached per "<console>/<artwork folder>": file name -> document uri. */
    private val retroArchListings = ConcurrentHashMap<String, Map<String, Uri>>()
    private var retroArchListingsRoot: String? = null

    val localThumbnailsDirectory: File
        get() = File(appContext.getExternalFilesDir(null), "thumbnails")

    suspend fun resolve(target: CoverTarget): Resolved? =
        withContext(Dispatchers.IO) {
            findInRetroArchFolder(target)?.let { return@withContext Resolved(it, false) }
            findInMainFolder(target)?.let { return@withContext Resolved(it, false) }
            findInLocalFolder(target)?.let { return@withContext Resolved(it, false) }
            if (target in missing) return@withContext null

            val downloaded = download(target)
            if (downloaded == null) missing.add(target)
            downloaded?.let { Resolved(it, true) }
        }

    private fun localFile(
        target: CoverTarget,
        name: String,
    ): File {
        return File(localThumbnailsDirectory, "${target.systemFolder}/${target.artworkType.libretroFolder}/$name.png")
    }

    private fun findInLocalFolder(target: CoverTarget): File? {
        return target.names
            .map { localFile(target, it) }
            .firstOrNull { it.exists() && it.length() > 0 }
    }

    private fun mainFolderListing(target: CoverTarget): MainFolderListing? {
        val root = mainFolder.getRoot() ?: return null
        val rootValue = root.uri.toString()
        if (rootValue != mainFolderListingsRoot) {
            mainFolderListings.clear()
            mainFolderListingsRoot = rootValue
        }
        val key = "${target.mainFolderSystem}/${target.artworkType.libretroFolder}"
        return mainFolderListings.getOrPut(key) {
            val folder =
                runCatching {
                    mainFolder.folder(MainFolder.THUMBNAILS, target.mainFolderSystem, target.artworkType.libretroFolder)
                }.getOrNull()
            val files = runCatching { folder?.children()?.mapValues { it.value.uri } }.getOrNull() ?: emptyMap()
            MainFolderListing(folder, ConcurrentHashMap(files))
        }
    }

    private fun findInMainFolder(target: CoverTarget): Uri? {
        val listing = mainFolderListing(target) ?: return null
        return target.names.firstNotNullOfOrNull { listing.files["$it.png"] }
    }

    private fun saveToMainFolder(
        target: CoverTarget,
        bytes: ByteArray,
    ): Uri? {
        val listing = mainFolderListing(target) ?: return null
        val folder = listing.folder ?: return null
        val fileName = "${target.exactName}.png"
        return runCatching { folder.write(fileName, bytes, null) }
            .onSuccess { listing.files[fileName] = it }
            .onFailure { Timber.w(it, "Could not store artwork $fileName in the Lemuroid-S folder") }
            .getOrNull()
    }

    private fun download(target: CoverTarget): Any? {
        for (url in CoverUrls.candidates(target)) {
            val bytes =
                runCatching {
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) response.body?.bytes() else null
                    }
                }.getOrNull()

            if (bytes != null && bytes.isNotEmpty()) {
                // Saved under the exact game name, as RetroArch would, even if a fallback name matched.
                saveToMainFolder(target, bytes)?.let { return it }

                val file = localFile(target, target.exactName)
                return runCatching {
                    file.parentFile?.mkdirs()
                    file.writeBytesAtomic(bytes)
                    file
                }.onFailure { Timber.w(it, "Could not store artwork $file") }.getOrNull()
            }
        }
        return null
    }

    private fun findInRetroArchFolder(target: CoverTarget): Uri? {
        val rootValue =
            SharedPreferencesHelper.getSharedPreferences(appContext)
                .getString(appContext.getString(R.string.pref_key_artwork_retroarch_folder), null)
                ?: return null

        if (rootValue != retroArchListingsRoot) {
            retroArchListings.clear()
            retroArchListingsRoot = rootValue
        }

        val key = "${target.systemFolder}/${target.artworkType.libretroFolder}"
        val listing =
            retroArchListings.getOrPut(key) {
                runCatching { listRetroArchFolder(Uri.parse(rootValue), target) }
                    .onFailure { Timber.w(it, "Could not read RetroArch thumbnails folder") }
                    .getOrNull() ?: emptyMap()
            }

        return target.names.firstNotNullOfOrNull { listing["$it.png"] }
    }

    private fun listRetroArchFolder(
        treeUri: Uri,
        target: CoverTarget,
    ): Map<String, Uri> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val systemId = findChildId(treeUri, rootId, target.systemFolder) ?: return emptyMap()
        val typeId = findChildId(treeUri, systemId, target.artworkType.libretroFolder) ?: return emptyMap()
        val files = listChildren(treeUri, typeId)
        return files.mapValues { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.value) }
    }

    private fun findChildId(
        treeUri: Uri,
        parentId: String,
        name: String,
    ): String? = listChildren(treeUri, parentId)[name]

    /** Children of a folder in a picked tree, as display name -> document id. */
    private fun listChildren(
        treeUri: Uri,
        parentId: String,
    ): Map<String, String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
        val result = mutableMapOf<String, String>()
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(1)] = cursor.getString(0)
            }
        }
        return result
    }

    companion object {
        @Volatile
        private var instance: CoverStore? = null

        fun get(context: Context): CoverStore {
            return instance ?: synchronized(this) {
                instance ?: CoverStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
