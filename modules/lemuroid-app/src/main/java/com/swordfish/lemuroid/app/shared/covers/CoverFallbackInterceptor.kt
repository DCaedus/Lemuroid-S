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

        val request =
            chain.request.newBuilder()
                .data(resolved.data)
                // Only fade in artwork that was just downloaded; stored artwork shows right away.
                .crossfade(resolved.downloaded)
                .build()
        return chain.proceed(request)
    }
}
