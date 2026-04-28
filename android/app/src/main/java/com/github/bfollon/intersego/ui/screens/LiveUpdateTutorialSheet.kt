/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.R
import kotlinx.coroutines.launch

private sealed class LiveUpdateSlideContent {
    data class Icon(val imageVector: ImageVector) : LiveUpdateSlideContent()
    data class Screenshot(@DrawableRes val imageRes: Int) : LiveUpdateSlideContent()
}

private data class LiveUpdateTutorialSlide(
    val content: LiveUpdateSlideContent,
    val title: String,
    val body: String,
)

private val liveUpdateTutorialSlides = listOf(
    LiveUpdateTutorialSlide(
        content = LiveUpdateSlideContent.Icon(Icons.Filled.PeopleAlt),
        title = "Actualizaciones en directo",
        body = "Los horarios son orientativos. Con tu ayuda y la de otros usuarios, podemos saber en tiempo real si el autobús está en marcha.",
    ),
    LiveUpdateTutorialSlide(
        content = LiveUpdateSlideContent.Screenshot(R.drawable.tutorial_live_boarding_button),
        title = "Confirma tu embarque",
        body = "Cuando estés en el autobús, pulsa el botón. Así avisas a los demás de que el bus está saliendo y contribuyes a mejorar los tiempos estimados.",
    ),
    LiveUpdateTutorialSlide(
        content = LiveUpdateSlideContent.Screenshot(R.drawable.tutorial_live_boarded),
        title = "Llegada estimada en tiempo real",
        body = "Si otro usuario ha confirmado su embarque en una parada anterior a la tuya, verás un tiempo estimado más preciso para tu próxima salida.",
    ),
)

@Composable
fun LiveUpdateTutorialSheet(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState { liveUpdateTutorialSlides.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip button
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, top = 4.dp)
            ) {
                Text("Omitir", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Slides
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            LiveUpdateTutorialSlideContent(slide = liveUpdateTutorialSlides[page])
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Page dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(liveUpdateTutorialSlides.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Next / Done button
        Button(
            onClick = {
                if (pagerState.currentPage < liveUpdateTutorialSlides.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onDismiss()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < liveUpdateTutorialSlides.size - 1) "Siguiente" else "Entendido",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LiveUpdateTutorialSlideContent(slide: LiveUpdateTutorialSlide) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val content = slide.content) {
                is LiveUpdateSlideContent.Icon -> Icon(
                    imageVector = content.imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                is LiveUpdateSlideContent.Screenshot -> Image(
                    painter = painterResource(content.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
            )
        }
    }
}
