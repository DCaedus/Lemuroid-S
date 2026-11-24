package com.swordfish.lemuroid.app.shared.covers

/** Image data for a game's artwork: the URLs are tried in order until one loads. */
data class CoverRequest(val urls: List<String>)
