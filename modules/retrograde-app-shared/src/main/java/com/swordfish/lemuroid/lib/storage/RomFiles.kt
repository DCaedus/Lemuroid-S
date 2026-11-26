package com.swordfish.lemuroid.lib.storage

import android.os.ParcelFileDescriptor

/**
 * The files of a game, opened for the emulator. Lemuroid-S hands every game to LibretroDroid as open
 * files (Storage Access Framework descriptors), zips included; the engine then reads them (unzipping
 * in memory), lets the core open them through the libretro VFS, or gives the core named links to
 * them (see LibretroDroid). Nothing is copied to a cache.
 */
sealed class RomFiles {
    /** [archiveEntry]: when the only file is a zip holding a single ROM, that ROM's name in the zip. */
    data class Virtual(val files: List<Entry>, val archiveEntry: String? = null) : RomFiles() {
        data class Entry(val filePath: String, val fd: ParcelFileDescriptor)
    }

    companion object {
        /** Folder the virtual paths appear in; names inside it are the files' real names. */
        const val VIRTUAL_FILE_PATH = "/virtual/file/path"
    }
}
