/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showBugReport: Bool = false
    @State private var showNoConsentAlert: Bool = false

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "Unknown"
        return "Versión \(version) (\(build))"
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Image("SplashIcon")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 80)
                            .clipShape(RoundedRectangle(cornerRadius: 18))

                        Text("InterSego")
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [.green, .blue],
                                    startPoint: .leading,
                                    endPoint: .trailing,
                                ),
                            )
                            .multilineTextAlignment(.center)

                        Text("Metropolitanos de Segovia")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)

                        Text(appVersion)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top)

                    // App Description
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Acerca de la aplicación")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("Esta aplicación le permite consultar los horarios de los autobuses interurbanos de Segovia de forma rápida y sencilla, con soporte offline.")
                            .font(.body)
                            .foregroundColor(.secondary)

                        Text("Ha sido desarrollada como un proyecto personal para ayudar a la comunidad. La aplicación no rastrea a sus usuarios con fines comerciales.")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }

                    Divider()

                    // Support Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Apoye el proyecto")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("Si la aplicación le resulta útil y quiere apoyar su desarrollo, puede invitarme a un café:")
                            .font(.body)
                            .foregroundColor(.secondary)

                        Button(action: {
                            openURL(URL(string: "https://ko-fi.com/bfollon")!)
                        }) {
                            HStack {
                                Image(systemName: "cup.and.saucer.fill")
                                    .foregroundColor(.white)
                                Text("Cómpreme un Ko-fi ☕")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.blue)
                            .cornerRadius(25)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    // TODO: Add App Store review section once InterSego is published on the App Store.

                    Divider()

                    // Source Code Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Código fuente")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("El código fuente está disponible en GitHub para consulta y auditoría:")
                            .font(.body)
                            .foregroundColor(.secondary)

                        Button(action: {
                            openURL(URL(string: "https://github.com/bFollon/InterSego")!)
                        }) {
                            HStack {
                                Image(systemName: "chevron.left.forwardslash.chevron.right")
                                    .foregroundColor(.white)
                                Text("Ver en GitHub")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.black)
                            .cornerRadius(25)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    Divider()

                    // Data Source Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Fuente de datos")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("Los horarios provienen de los PDFs oficiales publicados por Linecar, la empresa concesionaria del servicio de autobuses interurbanos de Segovia:")
                            .font(.body)
                            .foregroundColor(.secondary)

                        Button(action: {
                            openURL(URL(string: "https://www.linecar.es/metropolitano/segovia/")!)
                        }) {
                            HStack {
                                Image(systemName: "bus.fill")
                                    .foregroundColor(.white)
                                Text("Linecar — Metropolitano Segovia")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.green)
                            .cornerRadius(25)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    Divider()

                    // Contact & Feedback Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Contacto y sugerencias")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("¿Ha encontrado algún error en los horarios o tiene ideas para mejorar la app?")
                            .font(.body)
                            .foregroundColor(.secondary)

                        VStack(spacing: 12) {
                            Button(action: {
                                if MonitoringPreferencesService.shared.hasUserOptedIn() {
                                    showBugReport = true
                                } else {
                                    showNoConsentAlert = true
                                }
                            }) {
                                HStack {
                                    Image(systemName: "exclamationmark.triangle")
                                        .foregroundColor(.orange)
                                    Text("Reportar error")
                                        .foregroundColor(.primary)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.secondary)
                                        .font(.caption)
                                }
                                .padding()
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .cornerRadius(8)
                            }
                            .buttonStyle(PlainButtonStyle())

                            if let feedbackURL = makeMailtoURL(
                                subject: "Sugerencias y mejoras - InterSego",
                                body: "Me gustaría sugerir..."
                            ) {
                                Link(destination: feedbackURL) {
                                    HStack {
                                        Image(systemName: "lightbulb")
                                            .foregroundColor(.blue)
                                        Text("Enviar sugerencia")
                                            .foregroundColor(.primary)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .foregroundColor(.secondary)
                                            .font(.caption)
                                    }
                                    .padding()
                                    .background(Color(uiColor: .secondarySystemGroupedBackground))
                                    .cornerRadius(8)
                                }
                            }
                        }
                    }

                    Divider()

                    // Legal Notice
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Aviso legal")
                            .font(.headline)
                            .foregroundColor(.primary)

                        Text("Los horarios mostrados son informativos y pueden no reflejar cambios de última hora. Se recomienda confirmar la información con Linecar antes de desplazarse.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .italic()

                        Button(action: {
                            if let url = URL(string: "https://github.com/bFollon") {
                                openURL(url)
                            }
                        }) {
                            Text("Desarrollado por @bFollon")
                                .font(.caption)
                                .foregroundColor(.blue)
                                .underline()
                        }
                    }

                    Spacer(minLength: 50)
                }
                .padding(.horizontal, 20)
            }
            .navigationTitle("Acerca de")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
        }
        .sheet(isPresented: $showBugReport) {
            BugReportView()
        }
        .alert("Informes de error desactivados", isPresented: $showNoConsentAlert) {
            Button("Activar informes de error") {
                MonitoringPreferencesService.shared.setMonitoringEnabled(true)
                ErrorReportingService.shared.initialize()
                showBugReport = true
            }
            Button("Enviar por email") {
                if let url = makeMailtoURL(subject: "Reporte de error - InterSego", body: "") {
                    openURL(url)
                }
            }
            Button("Cancelar", role: .cancel) { }
        } message: {
            Text("Los informes de error están desactivados. Puedes activarlos para enviar el informe directamente, o reportar el error por email.")
        }
    }

    private func makeMailtoURL(subject: String, body: String) -> URL? {
        let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let encodedBody = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "mailto:bfollon.dev@icloud.com?subject=\(encodedSubject)&body=\(encodedBody)")
    }
}

#Preview {
    AboutView()
}
