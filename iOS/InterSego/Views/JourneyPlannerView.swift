/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

private enum PickerTarget: Identifiable {
    case origin
    case destination
    var id: Int { self == .origin ? 0 : 1 }
}

private enum TimeMode {
    case departAfter
    case arriveBefore
}

private struct Endpoint {
    let physicalStopId: String
    let name: String
}

/// Entry UI for the journey planner: choose origin/destination (stop or "mi ubicación"), a
/// departure date and either a departure time ("Salir a las") or an arrival deadline ("Llegar
/// antes de"), and search. See `docs/JOURNEY_PLANNER.md` for the search itself, in particular
/// "Arrive-before mode" for how the two time modes differ.
struct JourneyPlannerView: View {
    let supportedRouteIds: [String]
    let onSearch: (_ originId: String, _ originName: String, _ destinationId: String, _ destinationName: String, _ date: Date, _ departAfterMin: Int, _ arriveBeforeMin: Int?) -> Void

    @State private var origin: Endpoint?
    @State private var destination: Endpoint?
    @State private var pickerTarget: PickerTarget?
    @State private var date = Date()
    @State private var timeMode: TimeMode = .departAfter
    @State private var departureTime: Date?
    @State private var arriveBeforeTime = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date()
    @State private var showTimePicker = false
    @State private var recents = RecentJourneysService.recent()
    @State private var locationMessage: String?
    @State private var resolvingLocation = false

    private var isToday: Bool { Calendar.current.isDateInToday(date) }

    var body: some View {
        Form {
            Section {
                // Two-column layout in one row: fields stacked on the left, swap button as its
                // own column on the right, spanning both. Without .buttonStyle(.plain) on every
                // nested Button here, Form/List defaults to treating the whole row as a single
                // tap target, which is what was actually swallowing origin's and swap's taps in
                // earlier attempts - not the HStack layout itself.
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        endpointRow(label: "Origen", endpoint: origin) { pickerTarget = .origin }
                        Divider()
                        endpointRow(label: "Destino", endpoint: destination) { pickerTarget = .destination }
                    }
                    Button(action: swap) {
                        Image(systemName: "arrow.up.arrow.down")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }

            if let locationMessage {
                Section {
                    Text(locationMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                DatePicker("Fecha", selection: $date, in: Date()...(Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date()), displayedComponents: .date)
                    .onChange(of: date) { _, newDate in
                        // "Salir ahora" only makes sense for today; picking a future date
                        // needs an explicit time, so make sure one is set.
                        if !Calendar.current.isDateInToday(newDate), departureTime == nil {
                            departureTime = Date()
                        }
                    }
                Picker("Modo", selection: $timeMode) {
                    Text("Salir a las").tag(TimeMode.departAfter)
                    Text("Llegar antes de").tag(TimeMode.arriveBefore)
                }
                .pickerStyle(.segmented)
                if timeMode == .departAfter {
                    if isToday {
                        Toggle("Salir ahora", isOn: Binding(
                            get: { departureTime == nil },
                            set: { isNow in departureTime = isNow ? nil : Date() }
                        ))
                    }
                    if !isToday || departureTime != nil {
                        DatePicker(
                            isToday ? "Hora" : "Hora de salida",
                            selection: Binding(get: { departureTime ?? Date() }, set: { departureTime = $0 }),
                            displayedComponents: .hourAndMinute
                        )
                    }
                } else {
                    DatePicker("Llegar antes de", selection: $arriveBeforeTime, displayedComponents: .hourAndMinute)
                }
            }

            Section {
                Button("Buscar") {
                    guard let origin, let destination else { return }
                    RecentJourneysService.record(RecentJourney(
                        originStopId: origin.physicalStopId, originName: origin.name,
                        destinationStopId: destination.physicalStopId, destinationName: destination.name
                    ))
                    if timeMode == .arriveBefore {
                        let comps = Calendar.current.dateComponents([.hour, .minute], from: arriveBeforeTime)
                        let arriveBeforeMin = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
                        onSearch(origin.physicalStopId, origin.name, destination.physicalStopId, destination.name, date, 0, arriveBeforeMin)
                    } else {
                        let departAfterMin: Int = {
                            guard let departureTime else {
                                let now = Calendar.current.dateComponents([.hour, .minute], from: Date())
                                return (now.hour ?? 0) * 60 + (now.minute ?? 0)
                            }
                            let comps = Calendar.current.dateComponents([.hour, .minute], from: departureTime)
                            return (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
                        }()
                        onSearch(origin.physicalStopId, origin.name, destination.physicalStopId, destination.name, date, departAfterMin, nil)
                    }
                }
                .disabled(origin == nil || destination == nil || origin?.physicalStopId == destination?.physicalStopId)
                .frame(maxWidth: .infinity, alignment: .center)
            }

            if !recents.isEmpty {
                Section("Recientes") {
                    ForEach(recents) { recent in
                        Button(action: {
                            origin = Endpoint(physicalStopId: recent.originStopId, name: recent.originName)
                            destination = Endpoint(physicalStopId: recent.destinationStopId, name: recent.destinationName)
                        }) {
                            Label("\(recent.originName) → \(recent.destinationName)", systemImage: "clock.arrow.circlepath")
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        }
        .navigationTitle("Planificar viaje")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $pickerTarget) { target in
            NavigationStack {
                StopPickerView(
                    title: target == .origin ? "Origen" : "Destino",
                    supportedRouteIds: supportedRouteIds,
                    allowMyLocation: true,
                    onStopSelected: { stopId, name in
                        let endpoint = Endpoint(physicalStopId: stopId, name: name)
                        if target == .origin { origin = endpoint } else { destination = endpoint }
                        pickerTarget = nil
                    },
                    onMyLocationSelected: {
                        pickerTarget = nil
                        Task { await resolveMyLocation(for: target) }
                    }
                )
            }
        }
    }

    private func endpointRow(label: String, endpoint: Endpoint?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(endpoint?.name ?? "Elegir parada")
                    .foregroundStyle(endpoint == nil ? .secondary : .primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
    }

    private func swap() {
        let tmp = origin
        origin = destination
        destination = tmp
    }

    @MainActor
    private func resolveMyLocation(for target: PickerTarget) async {
        resolvingLocation = true
        locationMessage = nil
        do {
            let selection = try await ClosestStopService.shared.findClosest()
            let stop = selection.stop
            let physicalIds = (try? TimetableLoader().loadPhysicalStopIds(
                await RouteDataService.shared.getRoutesForStop(stopId: stop.id).first ?? ""
            )) ?? [:]
            let physicalId = physicalIds[stop.id] ?? stop.id
            let endpoint = Endpoint(physicalStopId: physicalId, name: stop.name)
            if target == .origin { origin = endpoint } else { destination = endpoint }

            // NOTE: unlike Android (which re-requests the raw location separately to compute an
            // honest distance), ClosestStopService here doesn't expose the winning distance
            // alongside the stop, and its location handling is private/non-reusable. Extending its
            // public API would touch other call sites (e.g. Landing's closest-stop button); a
            // second independent location fetch would violate this card's "reuse ClosestStopService,
            // don't write new location code" instruction. Reporting the stop name only for now -
            // flagged as a known gap versus Android's full "(250 m)" disclosure.
            locationMessage = "Parada más cercana: \(stop.name)"
        } catch {
            locationMessage = error.localizedDescription
        }
        resolvingLocation = false
    }
}

extension PickerTarget: Equatable {}
