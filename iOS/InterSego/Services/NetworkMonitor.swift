/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation
import Network

final class NetworkMonitor: @unchecked Sendable {
    static let shared = NetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.github.bfollon.intersego.networkmonitor")
    private let lock = NSLock()
    private var _currentPath: NWPath?

    private init() {
        _currentPath = monitor.currentPath
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            lock.withLock { self._currentPath = path }
            DebugConfig.debugPrint("NetworkMonitor: Status changed - \(path.status)")
        }
        monitor.start(queue: queue)
        DebugConfig.debugPrint("NetworkMonitor: Initialized")
    }

    private var currentPath: NWPath {
        lock.withLock { _currentPath! }
    }

    var isOnline: Bool {
        currentPath.status == .satisfied
    }

    var networkStateDescription: String {
        let path = currentPath

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
