/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// In-app form for submitting a bug report or suggestion to BugSink.
struct FeedbackFormView: View {
    let category: FeedbackCategory

    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var email: String = ""
    @State private var description: String = ""
    @State private var isSubmitting: Bool = false
    @State private var submitted: Bool = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationView {
            Group {
                if submitted {
                    submittedView
                } else {
                    formView
                }
            }
            .navigationTitle(category.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        dismiss()
                    }
                    .disabled(isSubmitting)
                }
            }
        }
    }

    private var formView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(category.prompt)
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    Text(category.fieldLabel)
                        .font(.caption)
                        .foregroundColor(.secondary)

                    TextEditor(text: $description)
                        .frame(minHeight: 100)
                        .padding(8)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(errorMessage != nil && description.isEmpty ? Color.red : Color.clear, lineWidth: 1)
                        )
                }

                if errorMessage != nil && description.isEmpty {
                    Text(category.emptyFieldError)
                        .font(.caption)
                        .foregroundColor(.red)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Nombre (opcional)")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    TextField("Tu nombre", text: $name)
                        .textFieldStyle(.plain)
                        .padding(12)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(8)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Email (opcional)")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    TextField("Para contactar contigo", text: $email)
                        .textFieldStyle(.plain)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .padding(12)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(8)
                }

                Button(action: submitReport) {
                    HStack {
                        if isSubmitting {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                        } else {
                            Text(category.submitButtonLabel)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(description.isEmpty ? Color.gray : Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
                .disabled(isSubmitting || description.isEmpty)

                Spacer()
            }
            .padding()
        }
    }

    private var submittedView: some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "\(category.icon).fill")
                .font(.system(size: 48))
                .foregroundColor(.blue)

            Text(category.successTitle)
                .font(.title2)
                .fontWeight(.bold)

            Text(category.successBody)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Spacer()

            Button(action: { dismiss() }) {
                Text("Cerrar")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .padding(.horizontal)
            .padding(.bottom, 30)
        }
        .padding()
    }

    private func submitReport() {
        if description.isEmpty {
            errorMessage = "required"
            return
        }
        errorMessage = nil
        isSubmitting = true

        Task {
            let success = await ErrorReportingService.shared.submitFeedback(
                name: name,
                email: email,
                message: description,
                category: category
            )
            isSubmitting = false
            if success {
                submitted = true
            } else {
                dismiss()
            }
        }
    }
}

#Preview {
    FeedbackFormView(category: .bug)
}
