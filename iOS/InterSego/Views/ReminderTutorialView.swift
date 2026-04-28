/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

private struct ReminderTutorialSlide {
    let image: String
    let title: String
    let body: String
}

struct ReminderTutorialView: View {
    let onDismiss: () -> Void

    private let slides: [ReminderTutorialSlide] = [
        ReminderTutorialSlide(
            image: "tutorial_bell_intro",
            title: "Recordatorios",
            body: "Junto a cada salida encontrarás una campana. Púlsala para configurar un aviso antes de que salga el autobús."
        ),
        ReminderTutorialSlide(
            image: "tutorial_bell_oneoff",
            title: "Aviso puntual",
            body: "Un toque activa un aviso puntual. Recibirás una notificación antes de la próxima vez que circule ese autobús."
        ),
        ReminderTutorialSlide(
            image: "tutorial_bell_daily",
            title: "Aviso diario",
            body: "Mantén pulsada la campana para un aviso diario. Recibirás la notificación cada día que circule ese autobús."
        ),
        ReminderTutorialSlide(
            image: "tutorial_reminders_screen",
            title: "Gestiona tus recordatorios",
            body: "Aquí puedes ver y cancelar recordatorios activos, y ajustar la antelación con la que quieres recibir el aviso."
        ),
    ]

    @State private var currentPage = 0

    var body: some View {
        VStack(spacing: 0) {
            // Skip button
            HStack {
                Spacer()
                Button("Omitir") { onDismiss() }
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
            }

            // Slides
            TabView(selection: $currentPage) {
                ForEach(slides.indices, id: \.self) { index in
                    SlideView(slide: slides[index])
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: currentPage)

            // Page dots
            HStack(spacing: 8) {
                ForEach(slides.indices, id: \.self) { index in
                    Circle()
                        .fill(index == currentPage ? Color.accentColor : Color(.systemGray4))
                        .frame(width: 8, height: 8)
                        .animation(.easeInOut, value: currentPage)
                }
            }
            .padding(.bottom, 16)

            // Next / Start button
            Button {
                if currentPage < slides.count - 1 {
                    withAnimation { currentPage += 1 }
                } else {
                    onDismiss()
                }
            } label: {
                Text(currentPage < slides.count - 1 ? "Siguiente" : "Empezar")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .background(Color(.systemBackground))
    }
}

private struct SlideView: View {
    let slide: ReminderTutorialSlide

    var body: some View {
        VStack(spacing: 20) {
            Image(slide.image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(0.12), radius: 8, y: 4)
                .padding(.horizontal, 24)

            VStack(spacing: 10) {
                Text(slide.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text(slide.body)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .padding(.horizontal, 8)
            }
            .padding(.horizontal, 24)

            Spacer(minLength: 0)
        }
        .padding(.top, 12)
    }
}
