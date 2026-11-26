package com.swordfish.lemuroid.app.mobile.feature.shortcuts

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.app.shared.covers.CoverStore
import com.swordfish.lemuroid.app.shared.covers.CoverUrls
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.deeplink.DeepLink
import com.swordfish.lemuroid.common.bitmap.cropToSquare
import com.swordfish.lemuroid.common.bitmap.toBitmap
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

class ShortcutsGenerator(
    private val appContext: Context,
    retrofit: Retrofit,
) {
    suspend fun pinShortcutForGame(game: Game) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        val bitmap = retrieveBitmap(game)

        val shortcutInfo =
            ShortcutInfo.Builder(appContext, "game_${game.id}")
                .setShortLabel(game.displayName())
                .setLongLabel(game.displayName())
                .setIntent(DeepLink.launchIntentForGame(appContext, game))
                .setIcon(Icon.createWithBitmap(bitmap))
                .build()

        shortcutManager.requestPinShortcut(shortcutInfo, null)
    }

    private suspend fun retrieveBitmap(game: Game): Bitmap =
        withContext(Dispatchers.IO) {
            val result =
                runCatching {
                    // Prefer custom cover URI (local file) if set
                    if (game.customCoverUri != null) {
                        val uri = Uri.parse(game.customCoverUri)
                        val inputStream = appContext.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream).cropToSquare()
                    } else {
                        downloadArtwork(game).cropToSquare()
                    }
                }
            result.getOrElse { retrieveFallbackBitmap(game) }
        }

    /** Same artwork as the library screens: stored on the phone, or downloaded once and stored. */
    private suspend fun downloadArtwork(game: Game): Bitmap {
        val target = CoverUrls.target(appContext, game) ?: throw IllegalStateException("No artwork for ${game.title}")
        val resolved = CoverStore.get(appContext).resolve(target)?.data
        val bitmap =
            when (resolved) {
                is java.io.File -> BitmapFactory.decodeFile(resolved.absolutePath)
                is Uri -> appContext.contentResolver.openInputStream(resolved)?.use { BitmapFactory.decodeStream(it) }
                else -> null
            }
        return bitmap ?: throw IllegalStateException("No artwork found for ${game.title}")
    }

    private fun retrieveFallbackBitmap(game: Game): Bitmap {
        val desiredIconSize = getDesiredIconSize()
        return CoverUtils.getFallbackDrawable(game).toBitmap(desiredIconSize, desiredIconSize)
    }

    private fun getDesiredIconSize(): Int {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.launcherLargeIconSize ?: 256
    }

    fun supportShortcuts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)!!
        return shortcutManager.isRequestPinShortcutSupported
    }
}
