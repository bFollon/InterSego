/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.github.bfollon.intersego.R
import com.github.bfollon.intersego.services.ErrorReportingService
import com.github.bfollon.intersego.services.MonitoringPreferencesService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val gradientColors = listOf(Color(0xFF34C759), Color(0xFF007AFF))
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName ?: "Unknown"
    val versionCode = packageInfo.longVersionCode
    val (showBugReportSheet, setShowBugReportSheet) = remember { mutableStateOf(false) }
    val (showNoConsentDialog, setShowNoConsentDialog) = remember { mutableStateOf(false) }

    if (showBugReportSheet) {
        BugReportSheet(
            onDismiss = { setShowBugReportSheet(false) }
        )
    }

    if (showNoConsentDialog) {
        AlertDialog(
            onDismissRequest = { setShowNoConsentDialog(false) },
            title = { Text("Informes de error desactivados") },
            text = { Text("Los informes de error están desactivados. Puedes activarlos para enviar el informe directamente, o reportar el error por email.") },
            confirmButton = {
                TextButton(onClick = {
                    setShowNoConsentDialog(false)
                    MonitoringPreferencesService.setMonitoringEnabled(true)
                    ErrorReportingService.initialize(context)
                    setShowBugReportSheet(true)
                }) {
                    Text("Activar informes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setShowNoConsentDialog(false)
                    openEmail(context, makeMailtoUrl(
                        subject = "Reporte de error - InterSego",
                        body = ""
                    ))
                }) {
                    Text("Enviar por email")
                }
            }
        )
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF3CA27A)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "InterSego",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "InterSego",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(gradientColors)
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Interurbanos de Segovia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Versión $versionName ($versionCode)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Description Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Acerca de la aplicación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Esta aplicación le permite consultar los horarios de los autobuses interurbanos de Segovia de forma rápida y sencilla, con soporte offline.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Ha sido desarrollada como un proyecto personal para ayudar a la comunidad. La aplicación no rastrea a sus usuarios con fines comerciales.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Support Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Apoye el proyecto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Si la aplicación le resulta útil y quiere apoyar su desarrollo, puede invitarme a un café:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { openUrl(context, "https://ko-fi.com/bfollon") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF29ABE0),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Cómpreme un Ko-fi ☕", fontWeight = FontWeight.Medium)
            }
        }

        // TODO: Add App Store review section once InterSego is published on the App Store / Google Play.

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Source Code Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Código fuente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "El código fuente está disponible en GitHub para consulta y auditoría:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { openUrl(context, "https://github.com/bFollon/InterSego") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF24292E),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ver en GitHub", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Data Source Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Fuente de datos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Los horarios provienen de los PDFs oficiales publicados por Linecar, la empresa concesionaria del servicio de autobuses interurbanos de Segovia:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { openUrl(context, "https://www.linecar.es/metropolitano/segovia/") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34C759),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Linecar — Metropolitano Segovia", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Map Data Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Datos del mapa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Los mapas de la app utilizan datos de © OpenStreetMap contributors, disponibles bajo la licencia Open Data Commons Open Database License (ODbL):",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { openUrl(context, "https://www.openstreetmap.org/copyright") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7EBC6A),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "OpenStreetMap — Copyright y licencia", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Contact & Feedback Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Contacto y sugerencias",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "¿Ha encontrado algún error en los horarios o tiene ideas para mejorar la app?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactCard(
                    label = "Reportar error",
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFFF9800)) },
                    onClick = {
                        if (MonitoringPreferencesService.hasUserOptedIn()) {
                            setShowBugReportSheet(true)
                        } else {
                            setShowNoConsentDialog(true)
                        }
                    }
                )

                ContactCard(
                    label = "Enviar sugerencia",
                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        openEmail(
                            context,
                            makeMailtoUrl(
                                subject = "Sugerencias y mejoras - InterSego",
                                body = "Me gustaría sugerir..."
                            )
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Legal Notice Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Aviso legal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Los horarios mostrados son informativos y pueden no reflejar cambios de última hora. Se recomienda confirmar la información con Linecar antes de desplazarse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )

            Text(
                text = "Desarrollado por @bFollon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openUrl(context, "https://github.com/bFollon") }
            )
        }

        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
private fun ContactCard(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun makeMailtoUrl(subject: String, body: String): String {
    val encodedSubject = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
    val encodedBody = java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    return "mailto:bfollon.dev@icloud.com?subject=$encodedSubject&body=$encodedBody"
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun openEmail(context: Context, mailtoUrl: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, mailtoUrl.toUri()))
}

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    AboutScreen { }
}
