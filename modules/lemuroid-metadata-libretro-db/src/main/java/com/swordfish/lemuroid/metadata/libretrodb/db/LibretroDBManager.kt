package com.swordfish.lemuroid.metadata.libretrodb.db

import android.content.Context
import androidx.room.Room

class LibretroDBManager(private val context: Context) {
    companion object {
        private const val DB_NAME = "libretro-db"
    }

    /**
     * Lemuroid-S: finds the system of games by file name without the extension ("Pokemon - Emerald
     * Version (USA, Europe)" -> "gba"), in one pass over the game database. Used to sort imported
     * RetroArch saves into system folders. Names not in the database are left out.
     */
    fun findSystemsByFileStem(stems: Set<String>): Map<String, String> {
        if (stems.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        dbInstance.openHelper.readableDatabase.query("SELECT romName, system FROM games").use { cursor ->
            while (cursor.moveToNext()) {
                val romName = cursor.getString(0) ?: continue
                val stem = romName.substringBeforeLast('.')
                if (stem in stems && stem !in result) {
                    cursor.getString(1)?.let { result[stem] = it }
                }
            }
        }
        return result
    }

    val dbInstance: LibretroDatabase by lazy {
        Room.databaseBuilder(context, LibretroDatabase::class.java, DB_NAME)
            .createFromAsset("libretro-db.sqlite")
            .fallbackToDestructiveMigration()
            .build()
    }
}
