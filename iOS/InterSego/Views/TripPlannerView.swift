/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Menu screen reached from Landing's "Planifica tu viaje" card. Styled like Landing's own
/// card list — a hub for date/trip-planning features. Currently a single entry
/// ("Consultar otro día"); the Journey Planner epic (E2) is expected to add a second one here.
struct TripPlannerView: View {
    let onCheckAnotherDay: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Button(action: onCheckAnotherDay) {
                HorizontalCard(label: "Consultar otro día", showChevron: true, isLoading: false) {
                    Image(systemName: "calendar")
                        .font(.system(size: 24))
                        .foregroundColor(.accentColor)
                }
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Planifica tu viaje")
        .navigationBarTitleDisplayMode(.inline)
    }
}
