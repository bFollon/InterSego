/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.intersego.services

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import java.util.concurrent.TimeUnit

/**
 * Coil Fetcher that serves OpenStreetMap tile PNGs from the persistent
 * TileCacheService before falling back to the network.
 *
 * Registered as a fetcher component so it intercepts all OSM tile URLs
 * transparently. Non-OSM URLs fall through to Coil's default HTTP fetcher.
 */
class OsmTileFetcher private constructor(
    private val url: String,
    private val options: Options,
    private val httpClient: OkHttpClient
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // Cache hit: serve from filesDir
        val cachedFile = TileCacheService.get(url)
        if (cachedFile != null) {
            DebugConfig.debugPrint("$TAG: Cache hit for $url")
            return@withContext SourceResult(
                source = ImageSource(
                    source = cachedFile.source().buffer(),
                    context = options.context
                ),
                mimeType = "image/png",
                dataSource = DataSource.DISK
            )
        }

        // Cache miss: fetch from OSM
        DebugConfig.debugPrint("$TAG: Cache miss, fetching $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val bytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val msg = "OSM tile fetch failed: HTTP ${response.code} for $url"
                DebugConfig.debugWarn("$TAG: $msg")
                error(msg)
            }
            val body = response.body
                ?: error("OSM tile response body was null for $url")
            body.bytes()
        }

        TileCacheService.put(url, bytes)

        val writtenFile = TileCacheService.get(url)
            ?: error("$TAG: Tile was written but cannot be read back: $url")

        SourceResult(
            source = ImageSource(
                source = writtenFile.source().buffer(),
                context = options.context
            ),
            mimeType = "image/png",
            dataSource = DataSource.NETWORK
        )
    }

    class Factory : Fetcher.Factory<Uri> {

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.host != OSM_TILE_HOST) return null
            return OsmTileFetcher(data.toString(), options, httpClient)
        }
    }

    companion object {
        private const val TAG = "OsmTileFetcher"
        private const val OSM_TILE_HOST = "tile.openstreetmap.org"
        private const val USER_AGENT =
            "InterSego/1.0 (Android; +https://github.com/bfollon/intersego; contact:bruno.follon@gmail.com)"
    }
}
