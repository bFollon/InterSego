/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Category of user feedback, used to drive copy across the feedback form,
/// BugSink submission, and email fallback.
enum FeedbackCategory: String, Identifiable {
    case bug
    case suggestion

    var id: String { rawValue }

    var title: String {
        switch self {
        case .bug: "Reportar error"
        case .suggestion: "Enviar sugerencia"
        }
    }

    var icon: String {
        switch self {
        case .bug: "exclamationmark.triangle"
        case .suggestion: "lightbulb"
        }
    }

    var prompt: String {
        switch self {
        case .bug: "Describe el problema que has encontrado:"
        case .suggestion: "Cuéntanos tu idea o sugerencia:"
        }
    }

    var fieldLabel: String {
        switch self {
        case .bug: "Descripción del error"
        case .suggestion: "Sugerencia"
        }
    }

    var fieldPlaceholder: String {
        switch self {
        case .bug: "¿Qué ha pasado? ¿Cuándo?"
        case .suggestion: "¿Qué te gustaría que mejoráramos?"
        }
    }

    var emptyFieldError: String {
        switch self {
        case .bug: "Por favor, describe el error"
        case .suggestion: "Por favor, escribe tu sugerencia"
        }
    }

    var submitButtonLabel: String {
        switch self {
        case .bug: "Enviar informe"
        case .suggestion: "Enviar sugerencia"
        }
    }

    var successTitle: String {
        switch self {
        case .bug: "¡Error reportado!"
        case .suggestion: "¡Gracias por tu sugerencia!"
        }
    }

    var successBody: String {
        switch self {
        case .bug: "Gracias por ayudar a mejorar la app."
        case .suggestion: "La tendremos en cuenta para mejorar la app."
        }
    }

    /// Header written into the BugSink message body.
    var bugSinkHeader: String {
        switch self {
        case .bug: "=== User Bug Report ==="
        case .suggestion: "=== User Suggestion ==="
        }
    }

    /// Fingerprint tag used to group BugSink events by category.
    var fingerprintTag: String {
        switch self {
        case .bug: "user-bug-report"
        case .suggestion: "user-suggestion"
        }
    }

    var mailtoSubject: String {
        switch self {
        case .bug: "Reporte de error - InterSego"
        case .suggestion: "Sugerencias y mejoras - InterSego"
        }
    }

    var mailtoBody: String {
        switch self {
        case .bug: ""
        case .suggestion: "Me gustaría sugerir..."
        }
    }
}
