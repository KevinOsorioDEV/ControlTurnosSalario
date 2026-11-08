package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmanu.turnos.domain.PayPeriod
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private val locale = Locale.forLanguageTag("es-CO")
private val symbols = DecimalFormatSymbols(locale).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", locale)
private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM", locale)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

fun money(value: Long): String = (if (value < 0) "-$" else "$") + DecimalFormat("#,##0", symbols).format(abs(value))
fun hours(minutes: Long): String = DecimalFormat("0.##", symbols).format(minutes / 60.0) + " h"
fun LocalTime.label(): String = format(timeFormat)
fun LocalDate.label(): String = format(dateFormat)
fun LocalDateTime.timeLabel(): String = toLocalTime().label()

fun PayPeriod.title(): String = month.format(monthFormat).replaceFirstChar { it.uppercase(locale) }
fun PayPeriod.subtitle(): String =
    "Corte ${noveltiesFrom.format(shortDateFormat)} – ${noveltiesTo.format(shortDateFormat)} · pago ${payday.format(shortDateFormat)}"

/** Hora actual que se refresca sola, para el contador del turno abierto. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun PeriodSelector(period: PayPeriod, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Corte anterior")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(period.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                period.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Corte siguiente")
        }
    }
}

@Composable
fun AmountRow(label: String, value: String, detail: String? = null, emphasized: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            value,
            style = (if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerHost(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = state) }
}

/** Fila con botones independientes para cambiar la fecha y la hora. */
@Composable
fun DateTimeField(label: String, value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingDate = true }, Modifier.weight(1f)) { Text(value.toLocalDate().label()) }
            OutlinedButton(onClick = { pickingTime = true }, Modifier.weight(1f)) { Text(value.timeLabel()) }
        }
    }
    if (pickingDate) {
        DatePickerHost(value.toLocalDate(), { pickingDate = false }) {
            onChange(it.atTime(value.toLocalTime()))
            pickingDate = false
        }
    }
    if (pickingTime) {
        TimePickerDialog(value.toLocalTime(), { pickingTime = false }) {
            onChange(value.toLocalDate().atTime(it))
            pickingTime = false
        }
    }
}
