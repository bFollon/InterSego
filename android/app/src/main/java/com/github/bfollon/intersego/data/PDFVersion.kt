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

package com.github.bfollon.intersego.data

import kotlinx.serialization.Serializable

/**
 * Represents version information for a cached PDF file
 * Used to determine if local cache is up-to-date with remote version
 */
@Serializable
data class PDFVersion(
    val url: String,
    val lastModified: Long? = null,    // HTTP Last-Modified timestamp
    val contentLength: Long? = null,   // Content-Length from HTTP headers
    val etag: String? = null,          // ETag from HTTP headers
    val downloadDate: Long = System.currentTimeMillis()  // When we downloaded this version
)
