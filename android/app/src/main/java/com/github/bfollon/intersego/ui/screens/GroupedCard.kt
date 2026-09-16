/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** [GroupedCard]'s corner radius - exposed so callers layering a [PeekingBannerCard] behind one
 * (e.g. JourneyDetailScreen's "Pasos" card) can pass the same value to the banner, instead of a
 * second hardcoded radius that can drift out of sync with this one. */
val GroupedCardCornerRadius = 20.dp

/**
 * Rounded, grouped-list-style card matching iOS's inset-grouped `List`/`Form` sections - used by
 * the journey-planner screens (entry form, stop picker) so related rows read as one visual group,
 * with clear breaks between groups (e.g. area headers in the stop picker), the same way on both
 * platforms.
 */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GroupedCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(content = content)
    }
}
