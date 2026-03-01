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

package com.github.bfollon.linecapp.services

import android.content.Context
import java.io.File

private val OSM_URL_REGEX =
    Regex("""https?://[^/]*openstreetmap\.org/(\d+)/(\d+)/(\d+)\.png""")

/**
 * Persistent tile cache for OpenStreetMap PNG tiles.
 *
 * Stores tiles under filesDir/tiles/{zoom}/{x}/{y}.png, keyed by tile
 * coordinates so each physical tile is stored only once regardless of
 * how many bus stops share it.
 *
 * Tiles are written lazily as the user visits stops. Because bus stops
 * do not move, cached tiles are never expired or evicted automatically.
 */
object TileCacheService {

    private const val TAG = "TileCacheService"

    private lateinit var tileRootDir: File

    fun initialize(context: Context) {
        tileRootDir = File(context.filesDir, "tiles")
        if (!tileRootDir.exists()) {
            tileRootDir.mkdirs()
            DebugConfig.debugPrint("$TAG: Created tile cache directory: ${tileRootDir.absolutePath}")
        }
    }

    @Synchronized
    fun get(url: String): File? {
        val file = fileForUrl(url) ?: return null
        return if (file.exists()) file else null
    }

    @Synchronized
    fun put(url: String, bytes: ByteArray) {
        val file = fileForUrl(url) ?: run {
            DebugConfig.debugWarn("$TAG: Cannot cache — unrecognised tile URL: $url")
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
            DebugConfig.debugPrint("$TAG: Cached tile ${file.name} (${bytes.size} bytes)")
        } catch (e: Exception) {
            tmp.delete()
            DebugConfig.debugError("$TAG: Failed to write tile ${file.path}", e)
        }
    }

    fun clearAll() {
        if (!::tileRootDir.isInitialized) return
        tileRootDir.deleteRecursively()
        tileRootDir.mkdirs()
        DebugConfig.debugPrint("$TAG: Cleared all cached tiles")
    }

    fun getCacheStats(): Pair<Int, Long> {
        if (!::tileRootDir.isInitialized) return 0 to 0L
        var count = 0
        var totalBytes = 0L
        tileRootDir.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .forEach { count++; totalBytes += it.length() }
        return count to totalBytes
    }

    internal fun fileForUrl(url: String): File? =
        osmUrlToRelativePath(url)?.let { File(tileRootDir, it) }
}

/**
 * Maps an OSM tile URL to a relative path "zoom/x/y.png".
 * Returns null for non-OSM URLs. Extracted for unit testability.
 */
internal fun osmUrlToRelativePath(url: String): String? {
    val match = OSM_URL_REGEX.find(url) ?: return null
    val (zoom, x, y) = match.destructured
    return "$zoom/$x/$y.png"
}
