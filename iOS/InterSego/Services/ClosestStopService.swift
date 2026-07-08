/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import CoreLocation
import Foundation

enum ClosestStopError: LocalizedError {
    case locationPermissionDenied
    case locationUnavailable
    case noStopsFound

    var errorDescription: String? {
        switch self {
        case .locationPermissionDenied:
            "Permiso de ubicación denegado. Actívalo en Ajustes → InterSego para usar esta función."
        case .locationUnavailable:
            "No se pudo obtener tu ubicación. Comprueba que el GPS esté activado e inténtalo de nuevo."
        case .noStopsFound:
            "No se encontraron paradas disponibles en este momento."
        }
    }
}

/// Finds the bus stop closest to the user's current location across all supported routes.
@MainActor
final class ClosestStopService {
    static let shared = ClosestStopService()

    private let locationCoordinator = LocationCoordinator()

    private init() {}

    func findClosest() async throws -> StopSelection {
        // 1. Get user location
        let userLocation = try await locationCoordinator.requestLocation()
        DebugConfig.debugPrint("ClosestStopService: User location \(userLocation.coordinate.latitude), \(userLocation.coordinate.longitude)")

        // 2. Collect all unique stop candidates from every supported route
        let dayType = TimetableService.shared.getCurrentDayType()
        let supportedRouteIds = await RouteDataService.shared.getSupportedRoutes()

        var candidates: [StopCandidate] = []
        // Global dedup: each canonical stop ID appears at most once across all routes
        var seenStopIds = Set<String>()

        for routeId in supportedRouteIds {
            let views = await RouteDataService.shared.getRouteViews(routeId: routeId, dayType: dayType)

            for view in views {
                for viewStop in view.stops {
                    let stop = viewStop.stop
                    guard seenStopIds.insert(stop.id).inserted,
                          let lat = stop.resolvedLatitude,
                          let lon = stop.resolvedLongitude else { continue }

                    let stopLocation = CLLocation(latitude: lat, longitude: lon)
                    let distance = userLocation.distance(from: stopLocation)
                    candidates.append(StopCandidate(
                        stop: stop,
                        distance: distance,
                    ))
                }
            }
        }

        guard !candidates.isEmpty else { throw ClosestStopError.noStopsFound }

        candidates.sort { $0.distance < $1.distance }
        DebugConfig.debugPrint("ClosestStopService: Closest stop is \(candidates[0].stop.name) at \(Int(candidates[0].distance))m")

        // 3. If multiple stops are within 100 m of the minimum, tie-break by soonest departure
        let minDist = candidates[0].distance
        let tied = candidates.filter { $0.distance <= minDist + 100 }

        let winner: StopCandidate
        if tied.count == 1 {
            winner = tied[0]
        } else {
            DebugConfig.debugPrint("ClosestStopService: \(tied.count) stops within tie-break range, resolving by soonest departure")
            winner = await pickBySoonestDeparture(from: tied, dayType: dayType)
        }

        DebugConfig.debugPrint("ClosestStopService: Winner → \(winner.stop.name)")
        // No primaryRouteId: closest stop shows all lines
        return StopSelection(
            stop: winner.stop,
            primaryRouteId: nil,
            primaryViewId: nil,
        )
    }

    // MARK: - Private

    private func pickBySoonestDeparture(from candidates: [StopCandidate], dayType: DayType) async -> StopCandidate {
        let (hour, minute) = TimetableService.shared.getCurrentTime()
        let currentTotalMinutes = hour * 60 + minute

        var bestCandidate = candidates[0]
        var bestMinutesUntil = Int.max

        for candidate in candidates {
            // Load timetables for all routes serving this stop
            let routeIds = await RouteDataService.shared.getRoutesForStop(stopId: candidate.stop.id)
            for routeId in routeIds {
                let timetables = await TimetableService.shared.loadTimetables(routeId: routeId)
                let stopTimetables = timetables.filter { $0.stopId == candidate.stop.id && $0.dayType == dayType }

                for timetable in stopTimetables {
                    guard let next = timetable.getNextDepartures(
                        currentHour: hour, currentMinute: minute, limit: 1,
                    ).first else { continue }

                    let depTotalMinutes = next.hour * 60 + next.minute
                    let until = depTotalMinutes >= currentTotalMinutes
                        ? depTotalMinutes - currentTotalMinutes
                        : 1440 - currentTotalMinutes + depTotalMinutes

                    if until < bestMinutesUntil {
                        bestMinutesUntil = until
                        bestCandidate = candidate
                    }
                }
            }
        }

        return bestCandidate
    }
}

private struct StopCandidate {
    let stop: BusStop
    let distance: Double
}

// MARK: - Location coordinator

/// Bridges CLLocationManager callbacks to async/await. Must run on MainActor
/// so delegate callbacks are delivered on the correct thread.
@MainActor
private final class LocationCoordinator: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func requestLocation() async throws -> CLLocation {
        try await withCheckedThrowingContinuation { cont in
            self.continuation = cont
            switch manager.authorizationStatus {
            case .notDetermined:
                manager.requestWhenInUseAuthorization()
            // Continues in locationManagerDidChangeAuthorization
            case .denied, .restricted:
                self.continuation = nil
                cont.resume(throwing: ClosestStopError.locationPermissionDenied)
            case .authorizedWhenInUse, .authorizedAlways:
                manager.requestLocation()
            @unknown default:
                self.continuation = nil
                cont.resume(throwing: ClosestStopError.locationUnavailable)
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard continuation != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            continuation?.resume(throwing: ClosestStopError.locationPermissionDenied)
            continuation = nil
        default:
            break
        }
    }

    func locationManager(_: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.first else { return }
        continuation?.resume(returning: location)
        continuation = nil
    }

    func locationManager(_: CLLocationManager, didFailWithError error: Error) {
        if let clError = error as? CLError, clError.code == .denied {
            continuation?.resume(throwing: ClosestStopError.locationPermissionDenied)
        } else {
            continuation?.resume(throwing: ClosestStopError.locationUnavailable)
        }
        continuation = nil
    }
}
