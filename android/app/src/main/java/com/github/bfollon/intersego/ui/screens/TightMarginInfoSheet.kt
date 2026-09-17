/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.services.TripPlannerPrefs

/**
 * Explains why a transfer margin below [TripPlannerPrefs.RECOMMENDED_MIN_BUFFER] is riskier —
 * arrival/departure times are a prediction, not a live feed, so a tight connection leaves little
 * room for a small delay. Shared by every "i" info affordance next to a tight-margin warning:
 * the Settings buffer sliders, and the results-list per-journey/section warnings on
 * JourneyResultsScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TightMarginInfoSheet(onDismissRequest: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Margen ajustado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Las horas de llegada son siempre una previsión, no una posición en tiempo real: incluso los horarios oficiales de Linecar son una estimación, y el autobús puede pasar unos minutos antes o después.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Con un margen menor de ${TripPlannerPrefs.RECOMMENDED_MIN_BUFFER} min, un pequeño retraso en el primer autobús puede hacer que pierdas el de conexión.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
