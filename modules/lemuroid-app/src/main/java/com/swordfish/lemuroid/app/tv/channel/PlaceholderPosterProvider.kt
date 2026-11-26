package com.swordfish.lemuroid.app.tv.channel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.common.bitmap.toBitmap
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves placeholder posters (game initials on a colored square) to the Android TV home screen for
 * recent games without artwork. Lemuroid used an online service (fakeimg.pl) for this; Lemuroid-S
 * draws them on the device. Read-only, and only serves the images it generated.
 */
class PlaceholderPosterProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!FILE_NAME.matches(name) || mode != "r") throw FileNotFoundException()
        val file = File(directory(context!!), name)
        if (!file.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        private const val SIZE_PX = 512
        private val FILE_NAME = Regex("""\d+\.png""")

        private fun directory(context: Context) = File(context.filesDir, "tv-placeholders").apply { mkdirs() }

        /** Draws (once) the placeholder poster for [game] and returns a uri the TV launcher can read. */
        fun posterUri(
            context: Context,
            game: Game,
        ): Uri {
            val file = File(directory(context), "${game.id}.png")
            if (!file.exists()) {
                val bitmap = CoverUtils.getFallbackDrawable(game).toBitmap(SIZE_PX, SIZE_PX)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            return Uri.parse("content://${context.packageName}.placeholders/${file.name}")
        }
    }
}
