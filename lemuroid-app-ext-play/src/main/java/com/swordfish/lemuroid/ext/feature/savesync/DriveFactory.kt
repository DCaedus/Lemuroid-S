/*
 * DriveFactory.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.ext.feature.savesync

import android.content.Context
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive

class DriveFactory(private val context: Context) {
    /** A Drive client for the connected account, or null if Drive access isn't granted. Blocks. */
    fun create(): Drive? {
        val token = DriveAuth.accessToken(context) ?: return null

        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val authorization =
            HttpRequestInitializer { request ->
                request.headers.authorization = "Bearer $token"
                request.setUnsuccessfulResponseHandler { _, response, _ ->
                    // An expired or revoked token: drop it so the next sync gets a fresh one.
                    if (response.statusCode == 401) DriveAuth.clearToken(context, token)
                    false
                }
            }

        return Drive.Builder(httpTransport, jsonFactory, authorization)
            .setApplicationName("com.dcaedus.lemuroids")
            .build()
    }
}
