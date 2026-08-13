/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI
import UIKit

/// "What's new" notice shown once after an app update — see `WhatsNewService`.
///
/// The entry list hugs its own content height when it fits (e.g. a single entry), and only
/// switches to a capped, scrollable region when it doesn't — `ViewThatFits` picks whichever
/// variant actually fits the available space, so the scrollable fallback leaves the next entry
/// visibly peeking/truncated at the bottom edge, the standard iOS/Material affordance for
/// "there's more, scroll" that doesn't need any custom scroll-position or size tracking.
struct WhatsNewView: View {
    @Binding var isPresented: Bool

    private static let maxEntriesHeight = UIScreen.main.bounds.height * 0.5

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 36))
                    .foregroundColor(.accentColor)

                Text("Novedades")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)

            Divider()

            let entriesToShow = WhatsNewService.entriesToShow()
            ViewThatFits(in: .vertical) {
                entriesList(entriesToShow)

                ScrollView {
                    entriesList(entriesToShow)
                }
                .frame(maxHeight: Self.maxEntriesHeight)
            }

            Divider()

            Button(action: dismiss) {
                Text("Entendido")
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.accentColor)
                    .cornerRadius(10)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(20)
    }

    @ViewBuilder
    private func entriesList(_ entries: [WhatsNewEntry]) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(entries.indices, id: \.self) { index in
                let entry = entries[index]
                Label {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(entry.title)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text(entry.body)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                } icon: {
                    Image(systemName: entry.icon)
                        .foregroundColor(.accentColor)
                }
            }
        }
    }

    private func dismiss() {
        WhatsNewService.markAsSeen()
        isPresented = false
    }
}

#Preview {
    WhatsNewView(isPresented: .constant(true))
}
