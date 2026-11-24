package com.swordfish.lemuroid.app.shared.covers

/** Image data for a game's artwork; resolved by [CoverFallbackInterceptor] through [CoverStore]. */
data class CoverRequest(val target: CoverTarget)
