/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.services.DeviceTokenService
import com.github.bfollon.intersego.services.GuidedModePrefs
import com.github.bfollon.intersego.services.MonitoringPreferencesService
import com.github.bfollon.intersego.services.NotificationPreferencesService
import com.github.bfollon.intersego.services.TripPlannerPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class SeverityOption(val value: String, val label: String, val description: String)
private val severityOptions = listOf(
    SeverityOption("info",     "Todas",            "Informativas, advertencias e interrupciones graves"),
    SeverityOption("warning",  "Solo importantes", "Advertencias y alertas críticas"),
    SeverityOption("critical", "Solo críticas",    "Únicamente interrupciones graves del servicio"),
    SeverityOption("none",     "Desactivadas",     "Sin notificaciones de alertas"),
)

private data class SeverityLevel(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var guidedModeEnabled by remember {
        mutableStateOf(GuidedModePrefs.isGuidedModeEnabled())
    }
    var errorsEnabled by remember {
        mutableStateOf(MonitoringPreferencesService.hasUserOptedIn())
    }
    var analyticsEnabled by remember {
        mutableStateOf(MonitoringPreferencesService.hasUserOptedInToAnalytics())
    }
    var alertMinSeverity by remember {
        mutableStateOf(NotificationPreferencesService.getAlertMinSeverity(context))
    }
    var showHowItWorks by remember { mutableStateOf(false) }
    var showTightMarginInfo by remember { mutableStateOf(false) }
    var maxWaitMin by remember { mutableStateOf(TripPlannerPrefs.getMaxWaitMin()) }
    var bufferSameStopTranscribed by remember { mutableStateOf(TripPlannerPrefs.getBufferSameStopTranscribed()) }
    var bufferSameStopEstimated by remember { mutableStateOf(TripPlannerPrefs.getBufferSameStopEstimated()) }
    var bufferWalkTranscribed by remember { mutableStateOf(TripPlannerPrefs.getBufferWalkTranscribed()) }
    var bufferWalkEstimated by remember { mutableStateOf(TripPlannerPrefs.getBufferWalkEstimated()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Section: Notificaciones
            Text(
                text = "Notificaciones",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHowItWorks = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Configura qué notificaciones quieres recibir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "¿Cómo funciona?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Alertas de servicio",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = severityOptions.find { it.value == alertMinSeverity }?.label ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            severityOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        expanded = false
                                        alertMinSeverity = option.value
                                        NotificationPreferencesService.saveChoice(context, option.value)
                                        val prefs = context.getSharedPreferences("fcm_prefs", android.content.Context.MODE_PRIVATE)
                                        val token = prefs.getString("fcm_token", null)
                                        if (token != null) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                DeviceTokenService.register(token, context, option.value)
                                            }
                                        }
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Modo guiado
            Text(
                text = "Modo guiado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                SettingsToggleRow(
                    title = "Mostrar selector de dirección",
                    subtitle = "Muestra una pantalla para seleccionar la dirección del autobús antes de ver las salidas",
                    checked = guidedModeEnabled,
                    onCheckedChange = { newValue ->
                        guidedModeEnabled = newValue
                        GuidedModePrefs.setGuidedModeEnabled(newValue)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Planifica tu viaje
            Text(
                text = "Planifica tu viaje",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsNumberRow(
                        title = "Espera máxima en transbordo",
                        subtitle = "Tiempo máximo de espera para que una conexión entre autobuses se considere válida.",
                        value = maxWaitMin,
                        minValue = 5,
                        maxValue = 240,
                        onValueChange = {
                            maxWaitMin = it
                            TripPlannerPrefs.setMaxWaitMin(it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNumberRow(
                        title = "Margen en la misma parada (horario exacto)",
                        subtitle = "Minutos mínimos entre bajar y coger el siguiente bus en la misma parada para que la conexión se considere válida, cuando el horario es oficial. Cuanto mayor, más seguras las conexiones, pero se muestran menos opciones.",
                        value = bufferSameStopTranscribed,
                        minValue = 0,
                        maxValue = 30,
                        onValueChange = {
                            bufferSameStopTranscribed = it
                            TripPlannerPrefs.setBufferSameStopTranscribed(it)
                        },
                        showTightMarginWarning = bufferSameStopTranscribed < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER,
                        onTightMarginWarningClick = { showTightMarginInfo = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNumberRow(
                        title = "Margen en la misma parada (estimado)",
                        subtitle = "Igual, pero cuando la hora de llegada es una estimación, no un horario exacto.",
                        value = bufferSameStopEstimated,
                        minValue = 0,
                        maxValue = 30,
                        onValueChange = {
                            bufferSameStopEstimated = it
                            TripPlannerPrefs.setBufferSameStopEstimated(it)
                        },
                        showTightMarginWarning = bufferSameStopEstimated < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER,
                        onTightMarginWarningClick = { showTightMarginInfo = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNumberRow(
                        title = "Margen tras caminar (horario exacto)",
                        subtitle = "Minutos mínimos tras un transbordo caminando a otra parada para que la conexión se considere válida, cuando el horario es oficial. Cuanto mayor, más seguras las conexiones, pero se muestran menos opciones.",
                        value = bufferWalkTranscribed,
                        minValue = 0,
                        maxValue = 30,
                        onValueChange = {
                            bufferWalkTranscribed = it
                            TripPlannerPrefs.setBufferWalkTranscribed(it)
                        },
                        showTightMarginWarning = bufferWalkTranscribed < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER,
                        onTightMarginWarningClick = { showTightMarginInfo = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNumberRow(
                        title = "Margen tras caminar (estimado)",
                        subtitle = "Igual, pero cuando la hora es una estimación, no un horario exacto.",
                        value = bufferWalkEstimated,
                        minValue = 0,
                        maxValue = 30,
                        onValueChange = {
                            bufferWalkEstimated = it
                            TripPlannerPrefs.setBufferWalkEstimated(it)
                        },
                        showTightMarginWarning = bufferWalkEstimated < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER,
                        onTightMarginWarningClick = { showTightMarginInfo = true }
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            TripPlannerPrefs.resetToDefaults()
                            maxWaitMin = TripPlannerPrefs.getMaxWaitMin()
                            bufferSameStopTranscribed = TripPlannerPrefs.getBufferSameStopTranscribed()
                            bufferSameStopEstimated = TripPlannerPrefs.getBufferSameStopEstimated()
                            bufferWalkTranscribed = TripPlannerPrefs.getBufferWalkTranscribed()
                            bufferWalkEstimated = TripPlannerPrefs.getBufferWalkEstimated()
                        }) {
                            Text("Restaurar valores")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Privacidad
            Text(
                text = "Privacidad",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsToggleRow(
                        title = "Monitoreo de Errores",
                        subtitle = "Datos técnicos anónimos para detectar fallos.",
                        checked = errorsEnabled,
                        onCheckedChange = { newValue ->
                            errorsEnabled = newValue
                            MonitoringPreferencesService.setMonitoringEnabled(newValue)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        title = "Analíticas de Uso",
                        subtitle = "Eventos de uso anónimos para mejorar la app.",
                        checked = analyticsEnabled,
                        onCheckedChange = { newValue ->
                            analyticsEnabled = newValue
                            MonitoringPreferencesService.setAnalyticsEnabled(newValue)
                        }
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Los cambios se aplican al reiniciar la app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    if (showTightMarginInfo) {
        ModalBottomSheet(
            onDismissRequest = { showTightMarginInfo = false },
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
                    text = "Con un margen menor de ${TripPlannerPrefs.RECOMMENDED_MIN_BUFFER} min, un pequeño retraso en el primer autobús puede hacer que pierdas el de conexión. Redúcelo solo si conoces bien la puntualidad de esa línea.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showHowItWorks) {
        val levels = listOf(
            SeverityLevel(Icons.Default.Notifications,    MaterialTheme.colorScheme.primary,            "Todas",            "Informativas, advertencias e interrupciones graves"),
            SeverityLevel(Icons.Default.Warning,          Color(0xFFF59E0BL),                           "Solo importantes", "Advertencias y alertas críticas"),
            SeverityLevel(Icons.Default.Error,            MaterialTheme.colorScheme.error,              "Solo críticas",    "Únicamente interrupciones graves del servicio"),
            SeverityLevel(Icons.Default.NotificationsOff, MaterialTheme.colorScheme.onSurfaceVariant,   "Desactivadas",     "Sin notificaciones de alertas"),
        )
        ModalBottomSheet(
            onDismissRequest = { showHowItWorks = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Niveles de alerta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                levels.forEachIndexed { index, level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = level.icon,
                            contentDescription = null,
                            tint = level.color,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = level.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index < levels.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNumberRow(
    title: String,
    subtitle: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    showTightMarginWarning: Boolean = false,
    onTightMarginWarningClick: () -> Unit = {}
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = value.toFloat(),
                onValueChange = { newValue ->
                    val rounded = newValue.roundToInt().coerceIn(minValue, maxValue)
                    text = rounded.toString()
                    onValueChange(rounded)
                },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    if (newText.length <= 3 && newText.all { it.isDigit() }) {
                        text = newText
                        newText.toIntOrNull()?.let { onValueChange(it.coerceIn(minValue, maxValue)) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(72.dp)
            )
        }
        if (showTightMarginWarning) {
            Spacer(modifier = Modifier.height(4.dp))
            TightMarginWarningPill(onClick = onTightMarginWarningClick)
        }
    }
}

@Composable
private fun TightMarginWarningPill(onClick: () -> Unit) {
    val warningColor = Color(0xFFF59E0BL)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(warningColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Margen ajustado — toca para más información",
            style = MaterialTheme.typography.labelSmall,
            color = warningColor
        )
    }
}
