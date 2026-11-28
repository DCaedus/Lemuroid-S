package com.swordfish.lemuroid.lib.core

import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.InputStream
import java.util.zip.ZipInputStream

interface CoreUpdater {
    suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    )

    /** Lemuroid-S: whether a core is already on the device, and its download size when known. */
    data class CoreStatus(val installed: Boolean, val sizeBytes: Long?)

    suspend fun coreStatus(
        context: Context,
        coreIDs: List<CoreID>,
    ): Map<CoreID, CoreStatus>

    interface CoreManagerApi {
        @GET
        @Streaming
        suspend fun downloadFile(
            @Url url: String,
        ): Response<InputStream>

        @HEAD
        suspend fun head(
            @Url url: String,
        ): Response<Void>

        @GET
        @Streaming
        suspend fun downloadZip(
            @Url url: String,
        ): Response<ZipInputStream>
    }
}
