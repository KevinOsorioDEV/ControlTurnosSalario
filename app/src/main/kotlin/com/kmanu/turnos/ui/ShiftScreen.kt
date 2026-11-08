package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftBreakdown
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun ShiftScreen(state: UiState, viewModel: AppViewModel) {
    val now = rememberNow()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Turno", style = MaterialTheme.typography.headlineMedium)
        val open = state.openShift
        if (open != null) {
            OpenShiftCard(open, state.settings.monthlySalary, now, viewModel::checkOut)
        } else {
            CheckInCard(state, now, viewModel::checkIn)
        }
        state.lastClosedShift?.let { LastShiftCard(it, state.settings.monthlySalary) }
    }
}

private fun nextOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(state: UiState, now: LocalDateTime, onCheckIn: (LocalDateTime, Int) -> Unit) {
    var chosenEnd by remember { mutableStateOf<LocalTime?>(null) }
    var breakOverride by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    val defaultEnd = now.plusMinutes(state.settings.defaultShiftMinutes.toLong()).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val endTime = chosenEnd ?: defaultEnd
    val scheduledEnd = nextOccurrence(endTime, now)
    val policy = state.settings.breakPolicy
    val policyBreak = policy.minutesFor(Duration.between(now, scheduledEnd).toMinutes())
    val breakText = breakOverride ?: policyBreak.toString()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sin turno activo", style = MaterialTheme.typography.titleMedium)
            Text("Salida programada", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                val day = if (scheduledEnd.toLocalDate() == now.toLocalDate()) "hoy" else "mañana"
                Text("${endTime.label()} ($day)")
            }
            if (state.frequentEndTimes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.frequentEndTimes.forEach { time ->
                        FilterChip(
                            selected = time == endTime,
                            onClick = { chosenEnd = time },
                            label = { Text(time.label()) },
                        )
                    }
                }
            }
            if (policy.minutes > 0) {
                OutlinedTextField(
                    value = breakText,
                    onValueChange = { breakOverride = it.filter(Char::isDigit) },
                    label = { Text("Almuerzo no pagado (minutos)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onCheckIn(scheduledEnd, breakText.toIntOrNull() ?: 0) },
                Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Marcar entrada") }
        }
    }

    if (picking) {
        TimePickerDialog(endTime, { picking = false }) {
            chosenEnd = it
            picking = false
        }
    }
}

@Composable
private fun OpenShiftCard(shift: Shift, salary: Long, now: LocalDateTime, onCheckOut: () -> Unit) {
    val breakdown = PayrollCalculator.breakdown(shift, salary, until = now)
    val extraMinutes = Duration.between(shift.scheduledEnd, now).toMinutes()
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Marcar salida?") },
            text = { Text("Cerrarás el turno a las ${now.timeLabel()} con ${hours(breakdown.workedMinutes)} trabajadas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onCheckOut()
                }) { Text("Marcar salida") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Seguir en turno") } },
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("En turno desde ${shift.start.timeLabel()}", style = MaterialTheme.typography.titleMedium)
            Text(hours(breakdown.workedMinutes), style = MaterialTheme.typography.displaySmall)
            Text(
                "Salida programada: ${shift.scheduledEnd.timeLabel()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (extraMinutes > 0) {
                Text(
                    "Llevas ${hours(extraMinutes)} de hora extra",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BreakdownLines(breakdown)
            Button(
                onClick = { confirming = true },
                Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Marcar salida") }
        }
    }
}

@Composable
private fun LastShiftCard(shift: Shift, salary: Long) {
    val breakdown = PayrollCalculator.breakdown(shift, salary)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Último turno", style = MaterialTheme.typography.titleMedium)
            Text(
                "${shift.start.toLocalDate().label()} · ${shift.start.timeLabel()} – ${shift.end?.timeLabel().orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            AmountRow("Horas trabajadas", hours(breakdown.workedMinutes))
            BreakdownLines(breakdown)
        }
    }
}

@Composable
fun BreakdownLines(breakdown: ShiftBreakdown) {
    breakdown.lines.forEach { line ->
        AmountRow(line.concept.label, money(line.amount), hours(line.minutes))
    }
    if (breakdown.lines.isNotEmpty()) {
        AmountRow("Recargos y extras", money(breakdown.total), emphasized = true)
    }
}
