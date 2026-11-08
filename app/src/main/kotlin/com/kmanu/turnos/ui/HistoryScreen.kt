package com.kmanu.turnos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmanu.turnos.domain.ColombiaHolidays
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private data class Editing(val shift: Shift, val isNew: Boolean)

@Composable
fun HistoryScreen(state: UiState, viewModel: AppViewModel) {
    var editing by remember { mutableStateOf<Editing?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Historial", style = MaterialTheme.typography.headlineMedium) }
            item { PeriodSelector(state.period, viewModel::previousPeriod, viewModel::nextPeriod) }
            if (state.periodShifts.isEmpty()) {
                item {
                    Text(
                        "No hay turnos en este corte.",
                        Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.periodShifts, key = { it.id }) { shift ->
                ShiftRow(shift, state.settings.monthlySalary) { editing = Editing(shift, isNew = false) }
            }
        }
        FloatingActionButton(
            onClick = {
                val start = LocalDate.now().atTime(LocalTime.of(7, 0))
                val end = start.plusMinutes(state.settings.defaultShiftMinutes.toLong())
                val breakMinutes = state.settings.breakPolicy.minutesFor(Duration.between(start, end).toMinutes())
                editing = Editing(Shift(UUID.randomUUID().toString(), start, end, end, breakMinutes), isNew = true)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Agregar turno") }
    }

    editing?.let { current ->
        ShiftEditor(
            original = current.shift,
            isNew = current.isNew,
            onDismiss = { editing = null },
            onSave = viewModel::save,
            onDelete = {
                viewModel.delete(current.shift.id)
                editing = null
            },
            onSaved = { editing = null },
        )
    }
}

@Composable
private fun ShiftRow(shift: Shift, salary: Long, onClick: () -> Unit) {
    val breakdown = shift.end?.let { PayrollCalculator.breakdown(shift, salary) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(shift.start.toLocalDate().label(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${shift.start.timeLabel()} – ${shift.end?.timeLabel() ?: "en curso"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (breakdown != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(hours(breakdown.workedMinutes), style = MaterialTheme.typography.bodyMedium)
                    if (breakdown.total > 0) {
                        Text(money(breakdown.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftEditor(
    original: Shift,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Shift) -> String?,
    onDelete: () -> Unit,
    onSaved: () -> Unit,
) {
    var start by remember { mutableStateOf(original.start) }
    var end by remember { mutableStateOf(original.end) }
    var scheduledEnd by remember { mutableStateOf(original.scheduledEnd) }
    var breakText by remember { mutableStateOf(original.breakMinutes.toString()) }
    var compensatoryRest by remember { mutableStateOf(original.compensatoryRest) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val touchesSundayOrHoliday = generateSequence(start.toLocalDate()) { it.plusDays(1) }
        .takeWhile { it <= (end ?: start).toLocalDate() }
        .any(ColombiaHolidays::isSundayOrHoliday)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Agregar turno" else "Editar turno") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateTimeField("Entrada", start) { start = it }
                end?.let { current -> DateTimeField("Salida", current) { end = it } }
                DateTimeField("Salida programada (después es extra)", scheduledEnd) { scheduledEnd = it }
                OutlinedTextField(
                    value = breakText,
                    onValueChange = { breakText = it.filter(Char::isDigit) },
                    label = { Text("Almuerzo no pagado (minutos)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (touchesSundayOrHoliday) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Pagar también la hora (sin compensatorio)",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = !compensatoryRest, onCheckedChange = { compensatoryRest = !it })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = original.copy(
                    start = start,
                    end = end,
                    scheduledEnd = scheduledEnd,
                    breakMinutes = breakText.toIntOrNull() ?: 0,
                    compensatoryRest = compensatoryRest,
                )
                val problem = onSave(updated)
                if (problem == null) onSaved() else error = problem
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("¿Eliminar este turno?") },
            text = { Text("Sus horas dejarán de contar en la liquidación. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }
}
