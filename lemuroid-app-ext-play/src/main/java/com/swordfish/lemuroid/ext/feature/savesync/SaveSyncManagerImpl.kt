package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.swordfish.lemuroid.common.kotlin.SharedPreferencesDelegates
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.ext.R
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.ExternalSavesStorage
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.DocumentFolder
import com.swordfish.lemuroid.lib.storage.MainFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat

class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
) : SaveSyncManager() {
    private val externalSavesStorage = ExternalSavesStorage(appContext)

    private var lastSyncTimestamp: Long by SharedPreferencesDelegates.LongDelegate(
        SharedPreferencesHelper.getSharedPreferences(appContext),
        appContext.getString(com.swordfish.lemuroid.lib.R.string.pref_key_last_save_sync),
        0L,
    )

    override fun getProvider(): String = "Google Drive"

    override fun getSettingsActivity(): Class<out Activity>? = ActivateGoogleDriveActivity::class.java

    override fun isSupported(): Boolean = true

    override fun isConfigured(): Boolean = GoogleSignIn.getLastSignedInAccount(appContext) != null

    override fun getLastSyncInfo(): String {
        val dateString =
            if (lastSyncTimestamp > 0) {
                SimpleDateFormat.getDateTimeInstance().format(lastSyncTimestamp)
            } else {
                "-"
            }
        return appContext.getString(R.string.gdrive_last_sync_completed, dateString)
    }

    override fun getConfigInfo(): String {
        val email = GoogleSignIn.getLastSignedInAccount(appContext)?.email
        return if (email != null) {
            appContext.getString(R.string.gdrive_connected_summary, email)
        } else {
            appContext.getString(R.string.gdrive_connected_none_summary)
        }
    }

    override suspend fun sync(cores: Set<CoreID>): Unit =
        withContext(Dispatchers.IO) {
            synchronized(SYNC_LOCK) {
                val saveSyncResult =
                    runCatching {
                        performSaveSyncForCores(cores)
                    }

                saveSyncResult.onFailure {
                    Timber.e(it, "Error while performing save sync.")
                }
            }
        }

    private fun performSaveSyncForCores(cores: Set<CoreID>) {
        val drive = DriveFactory(appContext).create() ?: return

        // Battery saves and states live in the Lemuroid-S folder (saves/<system>, states/<system>).
        mainFolderTree(MainFolder.SAVES)?.let {
            syncLocalAndRemoteFolder(drive, getOrCreateAppDataFolder("saves"), it, null)
        }

        // Saves that cores write themselves (PSP SAVEDATA, 3DS, DOS...) stay in app storage, as cores
        // can only write to real folders.
        syncLocalAndRemoteFolder(
            drive,
            getOrCreateAppDataFolder("core-saves"),
            LocalTree.Files(directoriesManager.getSavesDirectory()),
            null,
        )

        if (cores.isNotEmpty()) {
            mainFolderTree(MainFolder.STATES)?.let {
                syncLocalAndRemoteFolder(drive, getOrCreateAppDataFolder("states"), it, systemFolders(cores))
            }
            syncLocalAndRemoteFolder(
                drive,
                getOrCreateAppDataFolder("state-previews"),
                LocalTree.Files(directoriesManager.getStatesPreviewDirectory()),
                cores.map { it.coreName }.toSet(),
            )
        }

        lastSyncTimestamp = System.currentTimeMillis()
    }

    override fun computeSavesSpace(): String {
        val size = mainFolderTree(MainFolder.SAVES)?.files()?.values?.sumOf { it.size } ?: 0L
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }

    override fun computeStatesSpace(core: CoreID): String {
        val folders = systemFolders(setOf(core))
        val size =
            mainFolderTree(MainFolder.STATES)?.files()
                ?.filterKeys { path -> folders.any { path.startsWith(it) } }
                ?.values?.sumOf { it.size } ?: 0L
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }

    /** The Lemuroid-S saves or states folder, if it can be reached. */
    private fun mainFolderTree(name: String): LocalTree? {
        return externalSavesStorage.mainFolder.folder(name)?.let { LocalTree.Documents(it) }
    }

    /** Path prefixes ("gba/", ...) of the system folders whose states the [cores] write. */
    private fun systemFolders(cores: Set<CoreID>): Set<String> {
        return cores
            .flatMap { GameSystem.findSystemForCore(it) }
            .map { "${MainFolder.systemFolderName(it.id.dbname)}/" }
            .toSet()
    }

    private fun syncLocalAndRemoteFolder(
        drive: Drive,
        remoteFolderId: String,
        localTree: LocalTree,
        prefixes: Set<String>?,
    ) {
        val remoteFiles = getRemoteFiles(drive, remoteFolderId)
        val remoteFilesMap = buildRemoteFileMap(remoteFiles)
        val localFilesMap = localTree.files()

        getFilteredKeys(remoteFilesMap.keys + localFilesMap.keys, prefixes).forEach {
            handleFileSync(drive, remoteFolderId, localTree, it, remoteFilesMap[it], localFilesMap[it])
        }
    }

    private fun getFilteredKeys(
        keys: Set<String>,
        prefixes: Set<String>?,
    ): Set<String> {
        if (prefixes == null) return keys
        return keys.filter { key -> prefixes.any { key.startsWith(it) } }.toSet()
    }

    private fun handleFileSync(
        drive: Drive,
        remoteParentFolderId: String,
        localTree: LocalTree,
        path: String,
        remoteFile: com.google.api.services.drive.model.File?,
        localFile: LocalTree.Entry?,
    ) {
        Timber.i("Handling file pair: $path")

        runCatching {
            if (remoteFile != null && localFile == null) {
                downloadToLocal(drive, remoteFile, localTree, path)
            } else if (remoteFile == null && localFile != null) {
                onLocalOnly(remoteParentFolderId, localFile, drive)
            } else if (remoteFile != null && localFile != null) {
                if (areFileDifferent(remoteFile, localFile)) {
                    if (remoteFile.modifiedTime.value < localFile.lastModified) {
                        onLocalUpdated(localFile, drive, remoteFile)
                    } else if (remoteFile.modifiedTime.value > localFile.lastModified) {
                        downloadToLocal(drive, remoteFile, localTree, path)
                    }
                }
            }
        }.onFailure { Timber.w(it, "Could not sync $path") }
    }

    private fun areFileDifferent(
        remoteFile: com.google.api.services.drive.model.File,
        localFile: LocalTree.Entry,
    ): Boolean {
        if (remoteFile.modifiedTime.value == localFile.lastModified) {
            return false
        }

        if (remoteFile.size.toLong() != localFile.size) {
            return true
        }

        return remoteFile.md5Checksum != md5(localFile.read())
    }

    private fun onLocalUpdated(
        localFile: LocalTree.Entry,
        drive: Drive,
        remoteFile: com.google.api.services.drive.model.File,
    ) {
        Timber.i("Local file updated ${localFile.path}")

        val mediaContent = ByteArrayContent(MIME_TYPE, localFile.read())
        val metadata = com.google.api.services.drive.model.File()
        metadata.modifiedTime = DateTime(localFile.lastModified)
        drive.files().update(remoteFile.id, metadata, mediaContent)
            .execute()
    }

    private fun onLocalOnly(
        remoteParentFolderId: String,
        localFile: LocalTree.Entry,
        drive: Drive,
    ) {
        Timber.i("Local-only file detected ${localFile.path}")

        val metadata = com.google.api.services.drive.model.File()
        metadata.parents = listOf(remoteParentFolderId)
        metadata.name = localFile.path.substringAfterLast('/')
        metadata.appProperties = mapOf(GDRIVE_PROPERTY_LOCAL_PATH to localFile.path)
        metadata.modifiedTime = DateTime(localFile.lastModified)
        val mediaContent = ByteArrayContent(MIME_TYPE, localFile.read())
        drive.files().create(metadata, mediaContent)
            .setFields("id")
            .execute()
    }

    private fun downloadToLocal(
        drive: Drive,
        remoteFile: com.google.api.services.drive.model.File,
        localTree: LocalTree,
        path: String,
    ) {
        if (remoteFile.size == 0) return
        Timber.i("Downloading file to $path")
        val output = ByteArrayOutputStream()
        drive.files()
            .get(remoteFile.id)
            .executeMediaAndDownloadTo(output)
        localTree.write(path, output.toByteArray(), remoteFile.modifiedTime.value)
    }

    private fun buildRemoteFileMap(
        remoteFiles: Sequence<com.google.api.services.drive.model.File>,
    ): Map<String, com.google.api.services.drive.model.File> {
        return remoteFiles
            .filter { it.appProperties?.get(GDRIVE_PROPERTY_LOCAL_PATH) != null }
            .map { it.appProperties?.get(GDRIVE_PROPERTY_LOCAL_PATH)!! to it }
            .toMap()
    }

    private fun md5(data: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateAppDataFolder(folderName: String): String {
        val drive =
            DriveFactory(appContext).create()
                ?: throw UnsupportedOperationException()

        val query =
            drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$folderName' and mimeType = 'application/vnd.google-apps.folder'")
                .setFields("files(id)")
                .execute()

        if (query.files.size > 0) {
            return query.files[0].id
        }

        val metadata = com.google.api.services.drive.model.File()
        metadata.parents = listOf("appDataFolder")
        metadata.name = folderName
        metadata.mimeType = "application/vnd.google-apps.folder"

        val file =
            drive.files().create(metadata)
                .setFields("id")
                .execute()

        return file.id
    }

    private fun getRemoteFiles(
        drive: Drive,
        folderId: String,
    ): Sequence<com.google.api.services.drive.model.File> {
        var pageToken: String? = null
        return sequence {
            do {
                val query =
                    "'$folderId' in parents and trashed = false and mimeType = 'application/x-binary'"

                val fields =
                    "nextPageToken, " +
                        "files(id, name, size, appProperties, modifiedTime, parents, md5Checksum)"

                val result =
                    drive.files().list()
                        .setPageSize(500)
                        .setSpaces("appDataFolder")
                        .setQ(query)
                        .setFields(fields)
                        .setPageToken(pageToken)
                        .execute()

                yieldAll(result.files)
                pageToken = result.nextPageToken
            } while (pageToken != null)
        }
    }

    /** Files on the phone to sync, by path relative to the synced folder ("gba/Game.srm"). */
    private sealed interface LocalTree {
        class Entry(val path: String, val lastModified: Long, val size: Long, val read: () -> ByteArray)

        fun files(): Map<String, Entry>

        /** Writes [path] (creating folders), dated [lastModified] where the storage allows it. */
        fun write(
            path: String,
            data: ByteArray,
            lastModified: Long,
        )

        class Files(private val root: File) : LocalTree {
            override fun files(): Map<String, Entry> {
                return root.walkBottomUp()
                    .filter { it.isFile && it.length() > 0 }
                    .associate { file ->
                        val path = file.toRelativeString(root)
                        path to Entry(path, file.lastModified(), file.length()) { file.readBytes() }
                    }
            }

            override fun write(
                path: String,
                data: ByteArray,
                lastModified: Long,
            ) {
                val file = File(root, path)
                file.parentFile?.mkdirs()
                file.writeBytesAtomic(data)
                file.setLastModified(lastModified)
            }
        }

        /** The Lemuroid-S saves/states folder: files at the top and one folder per system. */
        class Documents(private val root: DocumentFolder) : LocalTree {
            override fun files(): Map<String, Entry> {
                val result = mutableMapOf<String, Entry>()
                collect(root, "", 0, result)
                return result
            }

            private fun collect(
                folder: DocumentFolder,
                prefix: String,
                depth: Int,
                result: MutableMap<String, Entry>,
            ) {
                for (child in folder.children().values) {
                    if (child.isDirectory) {
                        if (depth >= MAX_DEPTH) continue
                        val subFolder = folder.subFolder(child.name, create = false) ?: continue
                        collect(subFolder, "$prefix${child.name}/", depth + 1, result)
                    } else if (child.size > 0 && !child.name.endsWith(DocumentFolder.TEMP_SUFFIX)) {
                        val path = prefix + child.name
                        result[path] =
                            Entry(path, child.lastModified, child.size) { folder.read(child) ?: ByteArray(0) }
                    }
                }
            }

            override fun write(
                path: String,
                data: ByteArray,
                lastModified: Long,
            ) {
                val parts = path.split('/')
                val folder = root.subFolder(parts.dropLast(1)) ?: throw IllegalStateException("No folder for $path")
                folder.writeSafely(parts.last(), data)
            }

            companion object {
                private const val MAX_DEPTH = 1
            }
        }
    }

    companion object {
        private const val MIME_TYPE = "application/x-binary"
        const val GDRIVE_PROPERTY_LOCAL_PATH = "localPath"
        private val SYNC_LOCK = Object()
    }
}
