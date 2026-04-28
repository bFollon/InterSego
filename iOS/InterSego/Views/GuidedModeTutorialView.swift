/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

struct GuidedModeTutorialView: View {
    @State private var currentPage = 0
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $currentPage) {
                TutorialSlide1iOS()
                    .tag(0)

                TutorialSlide2iOS()
                    .tag(1)

                TutorialSlide3iOS()
                    .tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(maxHeight: .infinity)

            // Page indicator
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { page in
                    Circle()
                        .fill(page == currentPage ? Color.accentColor : Color.gray.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }
            .padding(.vertical, 16)

            // Buttons
            HStack(spacing: 12) {
                Button(action: onDismiss) {
                    Text("Saltar")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button(action: {
                    if currentPage < 2 {
                        withAnimation {
                            currentPage += 1
                        }
                    } else {
                        onDismiss()
                    }
                }) {
                    Text(currentPage < 2 ? "Siguiente" : "Listo")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(16)
        }
    }
}

private struct TutorialSlide1iOS: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "questionmark.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(.accentColor)

            Text("Modo guiado")
                .font(.title2)
                .fontWeight(.bold)

            Text("El modo guiado te ayuda a seleccionar la dirección correcta del autobús antes de ver las salidas.")
                .font(.callout)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()
        }
        .padding(.horizontal, 24)
    }
}

private struct TutorialSlide2iOS: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "arrow.left.arrow.right")
                .font(.system(size: 64))
                .foregroundColor(.accentColor)

            Text("Selecciona tu dirección")
                .font(.title2)
                .fontWeight(.bold)

            Text("Toca la dirección a la que deseas ir. Si hay varias líneas en la parada, verás todas las opciones claramente separadas.")
                .font(.callout)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()
        }
        .padding(.horizontal, 24)
    }
}

private struct TutorialSlide3iOS: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(.accentColor)

            Text("Recordamos tu elección")
                .font(.title2)
                .fontWeight(.bold)

            Text("La próxima vez que visites la misma parada y línea, la dirección que elegiste será sugerida automáticamente.")
                .font(.callout)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()
        }
        .padding(.horizontal, 24)
    }
}
