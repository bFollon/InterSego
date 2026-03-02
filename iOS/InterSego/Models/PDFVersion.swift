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

import Foundation

struct PDFVersion: Codable {
    let url: String
    let lastModified: TimeInterval?
    let contentLength: Int64?
    let etag: String?
    let downloadDate: TimeInterval

    init(url: String, lastModified: TimeInterval? = nil, contentLength: Int64? = nil,
         etag: String? = nil, downloadDate: TimeInterval = Date().timeIntervalSince1970) {
        self.url = url
        self.lastModified = lastModified
        self.contentLength = contentLength
        self.etag = etag
        self.downloadDate = downloadDate
    }
}
