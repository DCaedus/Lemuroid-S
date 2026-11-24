package com.swordfish.lemuroid.app.shared.covers

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult

/** Loads a [CoverRequest] by trying each of its URLs in order and returning the first that works. */
object CoverFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val coverRequest = chain.request.data as? CoverRequest ?: return chain.proceed(chain.request)

        var lastResult: ImageResult? = null
        for (url in coverRequest.urls) {
            val result = chain.proceed(chain.request.newBuilder().data(url).build())
            if (result is SuccessResult) return result
            lastResult = result
        }

        return lastResult ?: ErrorResult(
            drawable = null,
            request = chain.request,
            throwable = IllegalStateException("No artwork found"),
        )
    }
}
