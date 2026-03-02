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
import Network

final class NetworkMonitor: @unchecked Sendable {
    static let shared = NetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.github.bfollon.intersego.networkmonitor")
    private var currentPath: NWPath?

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            self?.currentPath = path
            DebugConfig.debugPrint("NetworkMonitor: Status changed - \(path.status)")
        }
        monitor.start(queue: queue)
        DebugConfig.debugPrint("NetworkMonitor: Initialized")
    }

    var isOnline: Bool {
        guard let path = currentPath else {
            // On first check before pathUpdateHandler fires, do a synchronous check
            let path = monitor.currentPath
            return path.status == .satisfied
        }
        return path.status == .satisfied
    }

    var networkStateDescription: String {
        guard let path = currentPath else { return "No inicializado" }

        guard path.status == .satisfied else { return "Sin conexión" }

        if path.usesInterfaceType(.wifi) {
            return "WiFi (conectado)"
        } else if path.usesInterfaceType(.cellular) {
            return "Datos móviles (conectado)"
        } else if path.usesInterfaceType(.wiredEthernet) {
            return "Ethernet (conectado)"
        }
        return "Conectado"
    }
}
