package com.kmanu.turnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kmanu.turnos.domain.AllowanceChange
import com.kmanu.turnos.domain.AllowanceHistory
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme
import java.time.LocalDate

private fun AllowanceChange.sinceLabel(): String =
    if (from == SalaryHistory.SINCE_ALWAYS) "Desde el inicio" else "Desde el ${from.longLabel()}"

/**
 * Auxilio de transporte o conectividad con su historial. Igual que el salario, cada cambio lleva
 * fecha: así un auxilio nuevo en enero no cambia las liquidaciones de los años anteriores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllowanceSheet(
    history: AllowanceHistory,
    salary: SalaryHistory,
    suggestedStart: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (AllowanceHistory) -> Unit,
    onRemove: (AllowanceHistory) -> Unit,
) {
    val current = history.changeAt(today)
    var legal by remember { mutableStateOf(current.isLegal) }
    var text by remember { mutableStateOf("") }
    var since by remember { mutableStateOf(suggestedStart) }
    var fromStart by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AllowanceChange?>(null) }
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val legalToday = AllowanceChange.legal(today).allowanceAt(today, salary.at(today))

    TurnosSheet(onDismiss) {
        Text("Auxilio de transporte o conectividad", style = MaterialTheme.typography.titleLarge)
        Text(
            "Cada corte usa el auxilio vigente el día de su pago. Por eso cada cambio lleva fecha y " +
                "las liquidaciones anteriores no se mueven.",
            style = MaterialTheme.typography.bodySmall,
            color = variant,
        )
        history.changes.asReversed().forEach { change ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !change.isLegal -> money(requireNotNull(change.amount))
                            change == current -> "Valor de ley · hoy ${money(legalToday)}"
                            else -> "Valor de ley"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(change.sinceLabel(), style = MaterialTheme.typography.bodySmall, color = variant)
                }
                when {
                    change == current -> StatusPill("Vigente", TurnosTheme.colors.closed)
                    change.from > today -> StatusPill("Próximo", TurnosTheme.colors.active)
                }
                if (history.changes.size > 1) {
                    IconButton(onClick = { removing = change }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar el auxilio ${change.sinceLabel().lowercase()}")
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Registrar un cambio", style = MaterialTheme.typography.titleSmall)
        PillTabs(
            options = listOf("Monto fijo", "Valor de ley"),
            selected = if (legal) 1 else 0,
            onSelect = {
                legal = it == 1
                showError = false
            },
        )
        if (legal) {
            Text(
                "Sigue el decreto de cada año, hoy ${money(legalToday)}. Si ganas más de dos salarios mínimos, " +
                    "la ley no lo da. No tienes que cambiarlo en enero: basta con actualizar la app.",
                style = MaterialTheme.typography.bodySmall,
                color = variant,
            )
        } else {
            FilledField(
                value = text,
                onValueChange = {
                    text = it.filter(Char::isDigit)
                    showError = false
                },
                label = "Auxilio que paga tu empresa",
                isError = showError,
                prefix = "$",
                transformation = ThousandsTransformation,
            )
            if (showError) {
                Text(
                    "Escribe el valor del auxilio, o 0 si no tienes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text("¿Desde cuándo aplica?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillChip(since.longLabel(), selected = !fromStart, icon = Icons.Rounded.CalendarMonth, onClick = { pickingDate = true })
            PillChip("Desde el inicio", selected = fromStart, onClick = { fromStart = !fromStart })
        }
        Text(
            if (fromStart) {
                "Reemplaza el auxilio inicial y recalcula todos los cortes. Úsalo si lo escribiste mal al empezar."
            } else {
                "Los cortes que se pagan desde esa fecha usan este auxilio; los anteriores conservan el que tenían."
            },
            style = MaterialTheme.typography.bodySmall,
            color = variant,
        )
        PrimaryAction("Guardar", onClick = {
            val start = if (fromStart) SalaryHistory.SINCE_ALWAYS else since
            val change = if (legal) {
                AllowanceChange.legal(start)
            } else {
                text.toLongOrNull()?.let { AllowanceChange(start, it) }
            }
            if (change == null) showError = true else onSave(history.withChange(change))
        })
    }

    if (pickingDate) {
        DatePickerHost(since, onDismiss = { pickingDate = false }) {
            since = it
            fromStart = false
            pickingDate = false
        }
    }

    removing?.let { change ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("¿Quitar este auxilio?") },
            text = { Text("${change.sinceLabel()}: los cortes de esas fechas usarán el auxilio anterior.") },
            confirmButton = {
                TextButton(onClick = {
                    history.without(change.from)?.let(onRemove)
                    removing = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}
