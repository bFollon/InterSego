/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// "What's new" notice shown once after an app update — see `WhatsNewService`.
struct WhatsNewView: View {
    @Binding var isPresented: Bool

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

            VStack(alignment: .leading, spacing: 16) {
                ForEach(WhatsNewService.entries.indices, id: \.self) { index in
                    let entry = WhatsNewService.entries[index]
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

    private func dismiss() {
        WhatsNewService.markAsSeen()
        isPresented = false
    }
}

#Preview {
    WhatsNewView(isPresented: .constant(true))
}
