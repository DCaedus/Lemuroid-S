package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.swordfish.lemuroid.common.drawable.TextDrawable
import com.swordfish.lemuroid.common.graphics.ColorUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers

object CoverUtils {
    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        imageView.load(coverData(imageView.context, game), imageView.context.imageLoader) {
            val fallbackDrawable = getFallbackDrawable(game)
            fallback(fallbackDrawable)
            error(fallbackDrawable)
        }
    }

    /**
     * What to load for a game's artwork: the user's custom cover if set, otherwise a [CoverRequest]
     * for the artwork type chosen in Settings (found or downloaded by [CoverStore]).
     */
    fun coverData(
        context: Context,
        game: Game,
    ): Any? {
        game.customCoverUri?.let { return it }
        return CoverUrls.target(context, game)?.let { CoverRequest(it) }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        // Artwork is stored permanently by CoverStore, so the temporary disk cache isn't used anymore.
        // Remove the one older test builds created.
        Thread { runCatching { applicationContext.cacheDir.resolve("image_cache").deleteRecursively() } }.start()

        return ImageLoader.Builder(applicationContext)
            .components { add(CoverFallbackInterceptor(CoverStore.get(applicationContext))) }
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .crossfade(true)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.displayName()
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.displayName().first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }
}
