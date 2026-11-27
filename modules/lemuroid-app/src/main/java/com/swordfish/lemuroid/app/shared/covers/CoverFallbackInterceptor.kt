package com.swordfish.lemuroid.app.shared.covers

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

/**
 * Loads a [CoverRequest]: finds the artwork on the phone (RetroArch's folder, then Lemuroid-S's)
 * or downloads it once and stores it permanently, then loads that file.
 */
class CoverFallbackInterceptor(private val coverStore: CoverStore) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val coverRequest = chain.request.data as? CoverRequest ?: return chain.proceed(chain.request)

        val resolved =
            coverStore.resolve(coverRequest.target)
                ?: return ErrorResult(
                    drawable = null,
                    request = chain.request,
                    throwable = IllegalStateException("No artwork found"),
                )

        // Only fade in artwork that was just downloaded; stored artwork shows right away. Compose
        // images do the fade themselves (over the game's generic tile), so they're only told.
        val fadesItself = chain.request.parameters.value<Boolean>(PARAM_FADES_ITSELF) == true
        val request =
            chain.request.newBuilder()
                .data(resolved.data)
                .crossfade(resolved.downloaded && !fadesItself)
                .setParameter(PARAM_DOWNLOADED, resolved.downloaded, memoryCacheKey = null)
                .build()
        return chain.proceed(request)
    }

    companion object {
        /** Set on requests whose image fades in by itself; Coil's crossfade is then skipped. */
        const val PARAM_FADES_ITSELF = "lemuroid.fades_itself"

        /** Set by the interceptor on the result's request: whether the artwork was just downloaded. */
        const val PARAM_DOWNLOADED = "lemuroid.downloaded"
    }
}
