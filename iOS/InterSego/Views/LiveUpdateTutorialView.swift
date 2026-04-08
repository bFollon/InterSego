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

import SwiftUI

private enum LiveUpdateSlideContent {
    case icon(String)
    case image(String)
}

private struct LiveUpdateTutorialSlide {
    let content: LiveUpdateSlideContent
    let title: String
    let body: String
}

struct LiveUpdateTutorialView: View {
    let onDismiss: () -> Void

    private let slides: [LiveUpdateTutorialSlide] = [
        LiveUpdateTutorialSlide(
            content: .icon("person.2.wave.2.fill"),
            title: "Actualizaciones en directo",
            body: "Los horarios son orientativos. Con tu ayuda y la de otros usuarios, podemos saber en tiempo real si el autobús está en marcha."
        ),
        LiveUpdateTutorialSlide(
            content: .image("tutorial_live_boarding_button"),
            title: "Confirma tu embarque",
            body: "Cuando estés en el autobús, pulsa el botón. Así avisas a los demás de que el bus está saliendo y contribuyes a mejorar los tiempos estimados."
        ),
        LiveUpdateTutorialSlide(
            content: .image("tutorial_live_boarded"),
            title: "ETA ajustada en tiempo real",
            body: "Si otro usuario ha confirmado su embarque en una parada anterior a la tuya, verás un tiempo estimado más preciso para tu próxima salida."
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
                    LiveUpdateSlideView(slide: slides[index])
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: currentPage)

            Spacer(minLength: 16)

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

            // Next / Done button
            Button {
                if currentPage < slides.count - 1 {
                    withAnimation { currentPage += 1 }
                } else {
                    onDismiss()
                }
            } label: {
                Text(currentPage < slides.count - 1 ? "Siguiente" : "Entendido")
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
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

private struct LiveUpdateSlideView: View {
    let slide: LiveUpdateTutorialSlide

    var body: some View {
        VStack(spacing: 20) {
            switch slide.content {
            case .icon(let systemName):
                Image(systemName: systemName)
                    .font(.system(size: 72))
                    .foregroundColor(.accentColor)
                    .padding(.vertical, 12)
                    .padding(.horizontal, 24)
            case .image(let name):
                Image(name)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.12), radius: 8, y: 4)
                    .padding(.horizontal, 24)
            }

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

        }
        .padding(.top, 12)
    }
}
