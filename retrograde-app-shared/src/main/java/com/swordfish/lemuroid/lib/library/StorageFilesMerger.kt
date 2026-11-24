package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.common.files.readLines
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.GroupedStorageFiles
import com.swordfish.lemuroid.lib.storage.StorageProvider

object StorageFilesMerger {
    /** Merge files which belong to the same game. This includes bin/cue and gdi files and m3u playlists.*/
    fun mergeDataFiles(
        storageProvider: StorageProvider,
        files: List<BaseStorageFile>,
    ): List<GroupedStorageFiles> {
        val allFiles =
            files
                .associateWith { listOf<BaseStorageFile>() }
                .toMutableMap()

        mergeBinCueFiles(allFiles, storageProvider)
        removeInvalidBinCuePairs(allFiles, storageProvider)
        mergeM3UPlaylists(allFiles, storageProvider)
        removeInvalidM3UPlaylists(allFiles, storageProvider)

        return allFiles.map { GroupedStorageFiles(it.key, it.value) }
    }

    private fun removeInvalidM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles: List<String> =
                    runCatching {
                        storageProvider.getInputStream(m3uFile.uri)?.readLines()
                    }.getOrNull() ?: listOf()

                val filesNames = allFiles[m3uFile]?.map { it.name } ?: listOf()

                if (!filesNames.containsAll(m3uFiles)) {
                    toBeRemoved.add(m3uFile)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles =
                    runCatching {
                        storageProvider.getInputStream(m3uFile.uri)?.readLines()
                    }.getOrNull() ?: listOf()

                val dataFiles = allFiles.filter { it.key.name in m3uFiles }

                allFiles[m3uFile] = allFiles[m3uFile]!! +
                    dataFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(dataFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidBinCuePairs(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension in TRACK_LISTS }
            .forEach {
                val requestedFileNames = extractBinFiles(storageProvider, it).toSet()
                val givenFileNames = allFiles[it]?.map { it.name }?.toSet() ?: setOf()

                if (requestedFileNames != givenFileNames) toBeRemoved.add(it)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeBinCueFiles(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension in TRACK_LISTS }
            .forEach { cueFile ->
                val requestedBinFiles = extractBinFiles(storageProvider, cueFile)

                val binFiles =
                    allFiles
                        .filter { it.key.name in requestedBinFiles }

                allFiles[cueFile] = (allFiles[cueFile] ?: listOf()) +
                    binFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(binFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    /** Track files listed by a .cue (FILE "name") or a Dreamcast .gdi ("1 0 4 2352 track01.bin 0"). */
    private fun extractBinFiles(
        storageProvider: StorageProvider,
        file: BaseStorageFile,
    ): List<String> {
        val pattern = if (file.extension == "gdi") GDI_TRACK else CUE_FILE
        return runCatching {
            storageProvider.getInputStream(file.uri)?.readLines()
                ?.mapNotNull { line ->
                    pattern.find(line)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                }
                ?: listOf()
        }.getOrDefault(listOf())
    }

    private val TRACK_LISTS = setOf("cue", "gdi")
    private val CUE_FILE = Regex("FILE \"(.*)\"")
    private val GDI_TRACK = Regex("""^\s*\d+\s+\d+\s+\d+\s+\d+\s+(?:"([^"]+)"|(\S+))""")
}
