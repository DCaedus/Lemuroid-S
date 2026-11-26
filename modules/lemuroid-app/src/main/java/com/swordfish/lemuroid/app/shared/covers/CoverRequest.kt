package com.swordfish.lemuroid.app.shared.covers

/**
 * Image data for a game's artwork; resolved by [CoverFallbackInterceptor] through [CoverStore].
 * [version] is [CoverStore.version] when requested: it changes when covers are added in bulk (e.g.
 * imported), so artwork that wasn't found before is looked up again.
 */
data class CoverRequest(val target: CoverTarget, val version: Int = 0)
