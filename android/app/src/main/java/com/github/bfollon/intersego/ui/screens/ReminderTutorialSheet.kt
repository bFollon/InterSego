/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follón
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

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.R
import kotlinx.coroutines.launch

private data class TutorialSlide(
    val imageRes: Int,
    val title: String,
    val body: String,
)

private val tutorialSlides = listOf(
    TutorialSlide(
        imageRes = R.drawable.tutorial_bell_intro,
        title = "Recordatorios",
        body = "Junto a cada salida encontrarás una campana. Púlsala para configurar un aviso antes de que salga el autobús.",
    ),
    TutorialSlide(
        imageRes = R.drawable.tutorial_bell_oneoff,
        title = "Aviso puntual",
        body = "Un toque activa un aviso puntual. Recibirás una notificación antes de la próxima vez que circule ese autobús.",
    ),
    TutorialSlide(
        imageRes = R.drawable.tutorial_bell_daily,
        title = "Aviso diario",
        body = "Mantén pulsada la campana para un aviso diario. Recibirás la notificación cada día que circule ese autobús.",
    ),
    TutorialSlide(
        imageRes = R.drawable.tutorial_reminders_screen,
        title = "Gestiona tus recordatorios",
        body = "Aquí puedes ver y cancelar recordatorios activos, y ajustar la antelación con la que quieres recibir el aviso.",
    ),
)

@Composable
fun ReminderTutorialSheet(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState { tutorialSlides.size }
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
            TutorialSlideContent(slide = tutorialSlides[page])
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Page dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(tutorialSlides.size) { index ->
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

        // Next / Start button
        Button(
            onClick = {
                if (pagerState.currentPage < tutorialSlides.size - 1) {
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
                text = if (pagerState.currentPage < tutorialSlides.size - 1) "Siguiente" else "Empezar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TutorialSlideContent(slide: TutorialSlide) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(slide.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
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
